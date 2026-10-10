package com.povzetkar.quarkus.service.transcript;

import com.povzetkar.quarkus.service.ProcessingContext;
import io.github.thoroldvix.api.TranscriptContent;
import io.github.thoroldvix.api.TranscriptContent.Fragment;
import io.github.thoroldvix.api.TranscriptList;
import io.github.thoroldvix.api.TranscriptRetrievalException;
import io.github.thoroldvix.api.YoutubeClient;
import io.github.thoroldvix.api.YoutubeTranscriptApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class YouTubeLegacyTranscriptProviderTest {

    private static final PauseRange IN_FETCH = new PauseRange(Duration.ofSeconds(1), Duration.ofSeconds(5));

    @Mock
    private YoutubeTranscriptApi youtubeTranscriptApi;

    @Mock
    private TranscriptList transcriptList;

    @Mock
    private io.github.thoroldvix.api.Transcript transcript;

    @Mock
    private TranscriptContent transcriptContent;

    @Mock
    private YouTubeRequestPacer pacer;

    private final List<YoutubeClient> clients = new ArrayList<>();
    private YouTubeLegacyTranscriptProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        provider = new YouTubeLegacyTranscriptProvider(client -> {
            clients.add(client);
            return youtubeTranscriptApi;
        }, pacer, IN_FETCH, true, new TimeTextFormatter());
        lenient().when(pacer.runInFetchSlot(any(), any()))
                .thenAnswer(inv -> ((YouTubeRequestPacer.FetchCall<?, ?>) inv.getArgument(1)).call());
    }

    private void stubTranscript(String text) throws Exception {
        Fragment fragment = mock(Fragment.class);
        when(fragment.getStart()).thenReturn(0.0);
        when(fragment.getText()).thenReturn(text);
        when(youtubeTranscriptApi.listTranscripts("videoId")).thenReturn(transcriptList);
        when(transcriptList.iterator()).thenAnswer(inv -> List.of(transcript).iterator());
        when(transcript.fetch()).thenReturn(transcriptContent);
        when(transcriptContent.getContent()).thenReturn(List.of(fragment));
    }

    private static io.github.thoroldvix.api.Transcript transcriptOf(String languageCode, boolean generated) throws Exception {
        io.github.thoroldvix.api.Transcript t = mock(io.github.thoroldvix.api.Transcript.class);
        lenient().when(t.getLanguageCode()).thenReturn(languageCode);
        lenient().when(t.isGenerated()).thenReturn(generated);
        TranscriptContent content = mock(TranscriptContent.class);
        Fragment fragment = mock(Fragment.class);
        lenient().when(fragment.getText()).thenReturn(languageCode + (generated ? " generated" : " manual"));
        lenient().when(content.getContent()).thenReturn(List.of(fragment));
        lenient().when(t.fetch()).thenReturn(content);
        return t;
    }

    /** The video's transcripts in YouTube's listing order. */
    private void stubAvailable(io.github.thoroldvix.api.Transcript... all) throws Exception {
        when(youtubeTranscriptApi.listTranscripts("videoId")).thenReturn(transcriptList);
        when(transcriptList.iterator()).thenAnswer(inv -> List.of(all).iterator());
    }
    @Test
    @DisplayName("language: English manual wins over English generated and other languages")
    void englishManualWins() throws Exception {
        stubAvailable(transcriptOf("de", false), transcriptOf("en", true), transcriptOf("en", false));

        assertThat(provider.fetch("videoId")).contains("en manual");
    }

    @Test
    @DisplayName("language: a manual English variant (en-GB) wins over generated English")
    void englishVariantManualWinsOverGeneratedEnglish() throws Exception {
        stubAvailable(transcriptOf("en", true), transcriptOf("en-GB", false));

        assertThat(provider.fetch("videoId")).contains("en-GB manual");
    }

    @Test
    @DisplayName("tie: exact en wins over an en-* variant of the same kind, whatever the listing order")
    void exactEnglishWinsTie() throws Exception {
        stubAvailable(transcriptOf("en-GB", false), transcriptOf("en", false));

        assertThat(provider.fetch("videoId")).contains("en manual");
    }

    @Test
    @DisplayName("tie: without English, the manual track in the spoken language (that of the generated track) wins")
    void spokenLanguageWinsTie() throws Exception {
        stubAvailable(transcriptOf("pt", false), transcriptOf("es", false), transcriptOf("es", true));

        assertThat(provider.fetch("videoId")).contains("es manual");
    }

    @Test
    @DisplayName("tie: otherwise the language code decides (deterministic, not the library's hash order)")
    void languageCodeDecidesRemainingTie() throws Exception {
        stubAvailable(transcriptOf("fr", false), transcriptOf("de", false));

        assertThat(provider.fetch("videoId")).contains("de manual");
    }

    @Test
    @DisplayName("language: English generated wins over a manual transcript in another language")
    void englishGeneratedWinsOverOtherManual() throws Exception {
        stubAvailable(transcriptOf("de", false), transcriptOf("en", true));

        assertThat(provider.fetch("videoId")).contains("en generated");
    }

    @Test
    @DisplayName("language: without English, a manual transcript in any language wins over a generated one")
    void otherManualWinsOverOtherGenerated() throws Exception {
        stubAvailable(transcriptOf("fr", true), transcriptOf("de", false));

        assertThat(provider.fetch("videoId")).contains("de manual");
    }

    @Test
    @DisplayName("language: a generated transcript in any language is the last resort")
    void otherGeneratedAsLastResort() throws Exception {
        stubAvailable(transcriptOf("sl", true));

        assertThat(provider.fetch("videoId")).contains("sl generated");
    }

    @Test
    @DisplayName("language: no transcript at all -> ProviderUnavailableException")
    void noTranscriptAtAll() throws Exception {
        stubAvailable();

        assertThatThrownBy(() -> provider.fetch("videoId"))
                .isInstanceOf(ProviderUnavailableException.class)
                .hasMessageContaining("youtube-legacy");
    }

    @Test
    @DisplayName("should return formatted transcript on success")
    void shouldReturnFormattedTranscript() throws Exception {
        stubTranscript("Hello world.");

        String result = provider.fetch("videoId");

        assertThat(result).contains("Hello world.");
    }

    @Test
    @DisplayName("should throw ProviderUnavailableException on TranscriptRetrievalException")
    void shouldThrowProviderUnavailableOnRetrievalException() throws Exception {
        when(youtubeTranscriptApi.listTranscripts("videoId"))
                .thenThrow(new TranscriptRetrievalException("videoId", "unavailable"));

        assertThatThrownBy(() -> provider.fetch("videoId"))
                .isInstanceOf(ProviderUnavailableException.class)
                .hasMessageContaining("youtube-legacy");
    }

    @Test
    @DisplayName("should throw ProviderUnavailableException on IllegalArgumentException")
    void shouldThrowProviderUnavailableOnInvalidFormat() throws Exception {
        when(youtubeTranscriptApi.listTranscripts("bad-id"))
                .thenThrow(new IllegalArgumentException("invalid video id"));

        assertThatThrownBy(() -> provider.fetch("bad-id"))
                .isInstanceOf(ProviderUnavailableException.class)
                .hasMessageContaining("youtube-legacy");
    }

    @Test
    @DisplayName("an unexpected runtime error of the library (unparsable YouTube response) is a provider failure")
    void libraryRuntimeErrorIsProviderUnavailable() throws Exception {
        when(youtubeTranscriptApi.listTranscripts("videoId")).thenThrow(new NullPointerException("captionTracks"));

        assertThatThrownBy(() -> provider.fetch("videoId"))
                .isInstanceOf(ProviderUnavailableException.class)
                .hasMessageContaining("youtube-legacy");
    }

    @Test
    @DisplayName("a skip raised inside the fetch (interrupted call) is passed on, not turned into a failure")
    void skipInsideFetchIsNotWrapped() throws Exception {
        when(youtubeTranscriptApi.listTranscripts("videoId")).thenThrow(new TranscriptSkippedException("interrupted"));

        assertThatThrownBy(() -> provider.fetch("videoId")).isInstanceOf(TranscriptSkippedException.class);
    }

    @Test
    @DisplayName("should report correct name and enabled state")
    void shouldReportNameAndEnabled() {
        assertThat(provider.name()).isEqualTo("youtube-legacy");
        assertThat(provider.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("automatic fetch runs inside a pacer slot with its own paced client")
    void automaticFetchRunsInPacerSlotWithPacedClient() throws Exception {
        stubTranscript("text");

        provider.fetch("videoId");
        provider.fetch("videoId");

        verify(pacer, times(2)).runInFetchSlot(eq(Optional.empty()), any());
        assertThat(clients).hasSize(2).doesNotHaveDuplicates()
                .allSatisfy(client -> assertThat(((YoutubeHttpClient) client).isPaced()).isTrue());
    }

    @Test
    @DisplayName("a run's fetch waits for the slot with the run's deadline and is counted for the run")
    void runFetchUsesRunDeadlineAndIsCounted() throws Exception {
        stubTranscript("text");
        ProcessingContext run = ProcessingContext.automatic(Instant.parse("2026-10-02T09:27:02Z"), Duration.ofMinutes(15));

        provider.fetch("videoId", run);

        verify(pacer).runInFetchSlot(eq(Optional.of(Instant.parse("2026-10-02T09:42:02Z"))), any());
        assertThat(run.youtubeFetches()).isEqualTo(1);
    }

    @Test
    @DisplayName("a skipped slot propagates, YouTube is not called and no fetch is counted")
    void skippedSlotPropagates() throws Exception {
        doThrow(new TranscriptSkippedException("budget used up")).when(pacer).runInFetchSlot(any(), any());
        ProcessingContext run = ProcessingContext.automatic(Instant.now(), Duration.ofMinutes(15));

        assertThatThrownBy(() -> provider.fetch("videoId", run)).isInstanceOf(TranscriptSkippedException.class);

        verifyNoInteractions(youtubeTranscriptApi);
        assertThat(run.youtubeFetches()).isZero();
    }

    @Test
    @DisplayName("manual fetch bypasses the pacer and uses an unpaced client")
    void manualFetchBypassesPacer() throws Exception {
        stubTranscript("Hello world.");

        String result = provider.fetchManual("videoId");

        assertThat(result).contains("Hello world.");
        verifyNoInteractions(pacer);
        assertThat(clients).singleElement()
                .satisfies(client -> assertThat(((YoutubeHttpClient) client).isPaced()).isFalse());
    }

    @Test
    @DisplayName("manual fetch failure maps to ProviderUnavailableException")
    void manualFetchFailureMapsToProviderUnavailable() throws Exception {
        when(youtubeTranscriptApi.listTranscripts("videoId"))
                .thenThrow(new TranscriptRetrievalException("videoId", "Status code: 429"));

        assertThatThrownBy(() -> provider.fetchManual("videoId"))
                .isInstanceOf(ProviderUnavailableException.class)
                .hasMessageContaining("youtube-legacy");
    }
}
