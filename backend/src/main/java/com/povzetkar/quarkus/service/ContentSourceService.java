package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.api.dto.ContentSourceCreateDTO;
import com.povzetkar.quarkus.api.exception.BusinessRuleException;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.SourceTypeEnum;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Creates content sources for the API (POV-219): from Quick Add on a topic ({@code newSources}) and from the
 * admin-only {@code POST /api/v1/sources}. (The legacy admin-only Qute UI still creates sources on its own —
 * its removal is in the backlog.)
 */
@ApplicationScoped
public class ContentSourceService {

    private static final Logger logger = LoggerFactory.getLogger(ContentSourceService.class);

    private final ContentSourceDetailService contentSourceDetailService;
    private final MeterRegistry registry;
    private final TransactionSynchronizationRegistry transactionSynchronizationRegistry;

    public ContentSourceService(ContentSourceDetailService contentSourceDetailService, MeterRegistry registry,
                                TransactionSynchronizationRegistry transactionSynchronizationRegistry) {
        this.contentSourceDetailService = contentSourceDetailService;
        this.registry = registry;
        this.transactionSynchronizationRegistry = transactionSynchronizationRegistry;
    }

    /** The source and whether this call created it. */
    public record Result(ContentSource source, boolean created) {
    }

    /**
     * Finds the source for the given platform identifier or creates it.
     * <p>
     * An entered channel ID that is already stored is returned without asking the platform. Otherwise the
     * identifier (handle, URL or channel ID) is resolved on the platform and the source is looked up and stored
     * under the canonical channel ID — ingestion reads the identifier as a channel ID, and one channel must not
     * exist under several identifiers. A legacy row stored under the entered text (handle/URL) is normalized to
     * the channel ID instead of being duplicated (PR #286 review). The counter counts committed creations. Runs in the
     * caller's transaction; a concurrent insert of the same new channel is not recovered from (POV-219 plan
     * P2/P4a): the unique constraint rejects the second request with an error, and repeating it finds the row.
     *
     * @throws BusinessRuleException 400 for an unknown or unsupported platform or a channel that does not exist,
     *                               503 if the platform could not be asked
     */
    @Transactional
    public Result findOrCreate(ContentSourceCreateDTO dto) {
        SourceTypeEnum sourceType = supportedSourceType(dto.platform());
        String identifier = dto.sourceIdentifier().trim();

        if (YouTubeDetailEnrichmentStrategy.CHANNEL_ID_PATTERN.matcher(identifier).matches()) {
            Optional<ContentSource> stored = ContentSource.findByIdentifier(identifier, sourceType.getId());
            if (stored.isPresent()) {
                return new Result(stored.get(), false);
            }
        }

        ContentSourceDetailService.ContentSourceDetails details = fetchDetails(sourceType, identifier)
                .orElseThrow(() -> new BusinessRuleException(Response.Status.BAD_REQUEST, "SOURCE_NOT_FOUND",
                        "No " + sourceType + " channel found for: " + identifier));

        Optional<ContentSource> existing = ContentSource.findByIdentifier(details.channelId(), sourceType.getId());
        if (existing.isPresent()) {
            return new Result(existing.get(), false);
        }
        Optional<ContentSource> legacy = identifier.equals(details.channelId())
                ? Optional.empty() : ContentSource.findByIdentifier(identifier, sourceType.getId());
        if (legacy.isPresent()) {
            ContentSource source = legacy.get();
            logger.info("Normalizing ContentSource {} identifier '{}' to channel ID {}", source.id, source.sourceIdentifier, details.channelId());
            source.sourceIdentifier = details.channelId();
            return new Result(source, false);
        }
        return new Result(create(sourceType, details, dto.name()), true);
    }

    private Optional<ContentSourceDetailService.ContentSourceDetails> fetchDetails(SourceTypeEnum sourceType, String identifier) {
        try {
            return contentSourceDetailService.fetchContentSourceDetails(sourceType.getId(), identifier);
        } catch (SourceDetailsUnavailableException e) {
            throw new BusinessRuleException(Response.Status.SERVICE_UNAVAILABLE, "SOURCE_CHECK_UNAVAILABLE",
                    "The " + sourceType + " channel check is unavailable right now; please try again later");
        }
    }

    private ContentSource create(SourceTypeEnum sourceType, ContentSourceDetailService.ContentSourceDetails details, String requestedName) {
        ContentSource source = new ContentSource();
        source.sourceIdentifier = details.channelId();
        source.sourceTypeId = sourceType.getId();
        source.name = Stream.of(details.name(), requestedName, details.channelId())
                .filter(name -> name != null && !name.isBlank())
                .findFirst()
                .orElseThrow();
        source.description = details.description();
        source.subscriberCount = details.subscriberCount();
        source.persist();

        countWhenCommitted(sourceType);
        logger.info("Created new ContentSource: {} [{}] (id: {})", source.name, source.sourceIdentifier, source.id);
        return source;
    }

    /** A creation rolled back with its topic (e.g. a later Quick Add entry fails) must not be counted. */
    private void countWhenCommitted(SourceTypeEnum sourceType) {
        transactionSynchronizationRegistry.registerInterposedSynchronization(new Synchronization() {
            @Override
            public void beforeCompletion() {
                // nothing to do before the commit
            }

            @Override
            public void afterCompletion(int status) {
                if (status == Status.STATUS_COMMITTED) {
                    registry.counter("povzetkar_source_added_total", Tags.of("platform", sourceType.name())).increment();
                }
            }
        });
    }

    private static SourceTypeEnum supportedSourceType(String platform) {
        SourceTypeEnum sourceType;
        try {
            sourceType = SourceTypeEnum.valueOf(platform);
        } catch (IllegalArgumentException _) { // platform is @NotBlank on the DTO
            throw new BusinessRuleException(Response.Status.BAD_REQUEST, "INVALID_PLATFORM", "Invalid platform: " + platform);
        }
        if (sourceType != SourceTypeEnum.YOUTUBE) {
            throw new BusinessRuleException(Response.Status.BAD_REQUEST, "PLATFORM_NOT_SUPPORTED",
                    "Platform " + sourceType + " is not supported yet");
        }
        return sourceType;
    }
}
