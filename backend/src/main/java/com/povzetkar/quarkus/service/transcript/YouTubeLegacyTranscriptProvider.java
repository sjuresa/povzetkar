package com.povzetkar.quarkus.service.transcript;

import com.povzetkar.quarkus.service.ProcessingContext;
import io.github.thoroldvix.api.TranscriptApiFactory;
import io.github.thoroldvix.api.TranscriptContent;
import io.github.thoroldvix.api.TranscriptContent.Fragment;
import io.github.thoroldvix.api.Transcript;
import io.github.thoroldvix.api.TranscriptList;
import io.github.thoroldvix.api.TranscriptRetrievalException;
import io.github.thoroldvix.api.YoutubeClient;
import io.github.thoroldvix.api.YoutubeTranscriptApi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;
import java.util.random.RandomGenerator;

/**
 * Fetches transcripts directly from YouTube via youtube-transcript-api. Automatic fetches are paced app-wide by
 * {@link YouTubeRequestPacer}; the manual capture is not (POV-222).
 */
@ApplicationScoped
public class YouTubeLegacyTranscriptProvider implements TranscriptProvider {

    private static final Logger log = LoggerFactory.getLogger(YouTubeLegacyTranscriptProvider.class);
    private static final String PROVIDER_NAME = "youtube-legacy";
    private static final String ENGLISH = "en";

    private final Function<YoutubeClient, YoutubeTranscriptApi> apiFactory;
    private final YouTubeRequestPacer pacer;
    private final PauseRange inFetchPause;
    private final TimeTextFormatter formatter;
    private final boolean enabled;
    private final HttpClient http = YoutubeHttpClient.newHttpClient();
    private final RandomGenerator random = new Random();

    @Inject
    public YouTubeLegacyTranscriptProvider(
            @ConfigProperty(name = "transcript.providers.youtube-legacy.enabled", defaultValue = "true") boolean enabled,
            YouTubeRequestPacer pacer,
            YouTubeLegacyPacingConfig pacing) {
        this(TranscriptApiFactory::createWithClient, pacer, pacing.inFetchPause(), enabled, new TimeTextFormatter());
    }

    YouTubeLegacyTranscriptProvider(Function<YoutubeClient, YoutubeTranscriptApi> apiFactory, YouTubeRequestPacer pacer,
                                    PauseRange inFetchPause, boolean enabled, TimeTextFormatter formatter) {
        this.apiFactory = apiFactory;
        this.pacer = pacer;
        this.inFetchPause = inFetchPause;
        this.formatter = formatter;
        this.enabled = enabled;
    }

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * The interface's base method — unused by the app ({@link TranscriptOrchestrator} calls {@code fetch(videoId,
     * context)} or {@link #fetchManual}). Kept paced (without a time budget) so no caller can bypass the queue.
     */
    @Override
    public String fetch(String videoId) throws ProviderUnavailableException {
        return fetch(videoId, ProcessingContext.manualItem());
    }

    /**
     * Paced fetch: waits for the app-wide fetch slot (giving up at the context's deadline), then makes its calls
     * with in-fetch pauses; a fetch actually made is counted on the context.
     *
     * @throws TranscriptSkippedException the slot was not obtained (see {@link YouTubeRequestPacer})
     */
    @Override
    public String fetch(String videoId, ProcessingContext context) throws ProviderUnavailableException {
        TranscriptContent content = pacer.runInFetchSlot(context.deadline(), () -> {
            context.recordYoutubeFetch();
            return download(videoId, YoutubeHttpClient.paced(http, inFetchPause, Thread::sleep, random));
        });
        return format(content);
    }

    /** Manual capture: no slot, no pauses. */
    @Override
    public String fetchManual(String videoId) throws ProviderUnavailableException {
        return format(download(videoId, YoutubeHttpClient.unpaced(http)));
    }

    private TranscriptContent download(String videoId, YoutubeClient client) throws ProviderUnavailableException {
        try {
            Transcript transcript = select(apiFactory.apply(client).listTranscripts(videoId));
            TranscriptContent content = transcript.fetch();
            log.info("YouTube transcript selected for video ID '{}': language '{}', generated {}.",
                    videoId, transcript.getLanguageCode(), transcript.isGenerated());
            return content;
        } catch (IllegalArgumentException e) {
            log.warn("Invalid video ID format '{}' for YouTube legacy provider.", videoId, e);
            throw new ProviderUnavailableException(PROVIDER_NAME, e);
        } catch (TranscriptRetrievalException e) {
            log.warn("Could not retrieve transcript for video ID '{}' via YouTube legacy provider.", videoId, e);
            throw new ProviderUnavailableException(PROVIDER_NAME, e);
        } catch (TranscriptSkippedException e) {
            throw e; // not a failure (interrupted call) — passed on as is
        } catch (RuntimeException e) {
            // the library dereferences YouTube's JSON without null checks — an unexpected response is a failed
            // attempt (counted, next provider tried), not an error that escapes the retry policy (POV-222)
            log.warn("Unexpected YouTube response for video ID '{}' via YouTube legacy provider.", videoId, e);
            throw new ProviderUnavailableException(PROVIDER_NAME, e);
        }
    }

    /**
     * English manual, English generated, then any manual, then any generated (POV-222); English includes regional
     * variants ({@code en-GB}). Ties are broken deterministically — the library lists tracks in hash order: exact
     * {@code en} before a variant, then the spoken language (that of a generated, i.e. speech-recognised, track)
     * before uploaded translations, then the language code. The original language, never a YouTube translation (an
     * extra call); the summary prompt asks Gemini for English.
     */
    private static Transcript select(TranscriptList transcripts) throws TranscriptRetrievalException {
        List<Transcript> all = new ArrayList<>();
        for (Transcript transcript : transcripts) {
            all.add(transcript);
        }
        String spoken = all.stream()
                .filter(Transcript::isGenerated)
                .map(YouTubeLegacyTranscriptProvider::code)
                .min(Comparator.naturalOrder())
                .orElse("");
        Comparator<Transcript> preference = Comparator
                .comparingInt(YouTubeLegacyTranscriptProvider::rank)
                .thenComparing(t -> !code(t).equals(ENGLISH))
                .thenComparing(t -> !code(t).equals(spoken))
                .thenComparing(YouTubeLegacyTranscriptProvider::code);
        return all.stream().min(preference).orElseThrow(() ->
                new TranscriptRetrievalException(transcripts.getVideoId(), "No transcripts in any language."));
    }

    private static int rank(Transcript transcript) {
        String code = code(transcript);
        boolean english = code.equals(ENGLISH) || code.startsWith(ENGLISH + "-");
        return (english ? 0 : 2) + (transcript.isGenerated() ? 1 : 0);
    }

    private static String code(Transcript transcript) {
        return Optional.ofNullable(transcript.getLanguageCode()).orElse("").toLowerCase(Locale.ROOT);
    }

    private String format(TranscriptContent content) {
        return formatter.format(toSegments(content.getContent()));
    }

    private List<TranscriptSegment> toSegments(List<Fragment> fragments) {
        return fragments.stream()
                .map(f -> new TranscriptSegment(f.getStart(), Optional.ofNullable(f.getText()).orElse("")))
                .toList();
    }
}
