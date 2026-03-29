package com.povzetkar.quarkus.service;

import com.google.api.services.youtube.model.Channel;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.SourceTypeEnum;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@ApplicationScoped
@EnrichmentStrategy(SourceTypeEnum.YOUTUBE)
public class YouTubeDetailEnrichmentStrategy implements ContentSourceDetailEnrichmentStrategy {

    private static final Logger logger = LoggerFactory.getLogger(YouTubeDetailEnrichmentStrategy.class);

    private static final Pattern HANDLE_PATTERN = Pattern.compile("^@([a-zA-Z0-9._-]+)$");
    private static final Pattern CHANNEL_ID_PATTERN = Pattern.compile("^UC[a-zA-Z0-9_-]{22}$");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://(?:www\\.)?youtube\\.com/(?:(@[a-zA-Z0-9._-]+)|(channel/|c/|user/)([a-zA-Z0-9._-]+))");

    private final YouTubeDataApiClient youtubeDataApiClient;
    private final int batchSize;
    private final long cacheExpiryMs;
    
    // Simple 1-hour in-memory cache for validation results
    private final Map<String, CachedItem<Optional<ContentSourceDetailService.ContentSourceDetails>>> validationCache = new ConcurrentHashMap<>();

    private record CachedItem<T>(T value, long expiry) {}

    @Inject
    public YouTubeDetailEnrichmentStrategy(YouTubeDataApiClient youtubeDataApiClient,
                                           @ConfigProperty(name = "youtube.api.batch-size", defaultValue = "50") int batchSize,
                                           @ConfigProperty(name = "youtube.validation.cache-expiry-ms", defaultValue = "3600000") long cacheExpiryMs) {
        this.youtubeDataApiClient = youtubeDataApiClient;
        this.batchSize = batchSize;
        this.cacheExpiryMs = cacheExpiryMs;
    }

    @Override
    public void enrich() {
        List<ContentSource> sourcesToEnrich = ContentSource.list(
                "sourceTypeId = ?1 and (name is null or name = '' or description is null or description = '')", SourceTypeEnum.YOUTUBE.getId());
        logger.info("Found {} YouTube content sources to enrich.", sourcesToEnrich.size());
        enrichSources(sourcesToEnrich, "Successfully enriched content source: {} ({})", "Could not enrich content source with identifier: {}");
    }

    @Override
    public void enrich(ContentSource source) {
        fetchDetails(source.sourceIdentifier).ifPresent(details -> {
            source.name = details.name();
            source.description = details.description();
            source.subscriberCount = details.subscriberCount();
        });
    }

    @Override
    public void refresh() {
        List<ContentSource> sourcesToRefresh = ContentSource.list("sourceTypeId = ?1", SourceTypeEnum.YOUTUBE.getId());
        logger.info("Found {} YouTube content sources to refresh.", sourcesToRefresh.size());
        enrichSources(sourcesToRefresh, "Successfully refreshed content source: {} ({})", "Could not refresh content source with identifier: {}");
    }

    @Override
    public Optional<ContentSourceDetailService.ContentSourceDetails> fetchDetails(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }

        CachedItem<Optional<ContentSourceDetailService.ContentSourceDetails>> cached = validationCache.get(identifier);
        if (cached != null && cached.expiry() > System.currentTimeMillis()) {
            return cached.value();
        }

        Optional<ContentSourceDetailService.ContentSourceDetails> details = doFetchDetails(identifier);
        validationCache.put(identifier, new CachedItem<>(details, System.currentTimeMillis() + cacheExpiryMs));
        return details;
    }

    public void clearCache() {
        validationCache.clear();
    }

    private Optional<ContentSourceDetailService.ContentSourceDetails> doFetchDetails(String input) {
        String resolved = resolveIdentifier(input);
        if (resolved == null) {
            logger.warn("Could not resolve YouTube identifier from input: {}", input);
            return Optional.empty();
        }

        try {
            Optional<Channel> channelOpt;
            if (resolved.startsWith("@")) {
                channelOpt = youtubeDataApiClient.fetchChannelByHandle(resolved);
            } else if (CHANNEL_ID_PATTERN.matcher(resolved).matches()) {
                List<Channel> items = youtubeDataApiClient.fetchChannels(List.of(resolved));
                channelOpt = items.stream().findFirst();
            } else {
                String usernameToFetch = resolved.startsWith("user:") ? resolved.substring(5) : resolved;
                channelOpt = youtubeDataApiClient.fetchChannelByUsername(usernameToFetch);
            }

            if (channelOpt.isEmpty()) {
                logger.warn("Channel not found for resolved identifier: {}", resolved);
                return Optional.empty();
            }

            return Optional.of(extractContentSourceDetails(channelOpt.get()));

        } catch (IOException e) {
            logger.error("Failed to fetch details for identifier: {}", resolved, e);
            return Optional.empty();
        }
    }

    private String resolveIdentifier(String input) {
        String trimmed = input.trim();
        
        // 1. Direct Handle
        if (HANDLE_PATTERN.matcher(trimmed).matches()) {
            return trimmed;
        }
        
        // 2. Direct ID
        if (CHANNEL_ID_PATTERN.matcher(trimmed).matches()) {
            return trimmed;
        }
        
        // 3. URL Extraction - requires explicit channel-related prefix
        Matcher urlMatcher = URL_PATTERN.matcher(trimmed);
        if (urlMatcher.find()) {
            if (urlMatcher.group(1) != null) {
                return urlMatcher.group(1); // It's an @handle
            }
            
            String prefix = urlMatcher.group(2);
            String candidate = urlMatcher.group(3);
            
            if ("channel/".equals(prefix) || CHANNEL_ID_PATTERN.matcher(candidate).matches()) {
                return candidate;
            }
            if ("user/".equals(prefix)) {
                return "user:" + candidate;
            }
            // For /c/ or other prefixes, treat as handle if not an ID
            return "@" + candidate;
        }

        return (trimmed.contains("://") || trimmed.contains("www.")) ? null : trimmed;
    }

    private void enrichSources(List<ContentSource> sources, String successLog, String failureLog) {
        for (int i = 0; i < sources.size(); i += batchSize) {
            List<ContentSource> batch = sources.subList(i, Math.min(i + batchSize, sources.size()));
            processBatch(batch, successLog, failureLog);
        }
    }

    private void processBatch(List<ContentSource> batch, String successLog, String failureLog) {
        List<String> channelIds = batch.stream().map(source -> source.sourceIdentifier).toList();
        logger.debug("Attempting to enrich/refresh batch of {} sources.", batch.size());

        try {
            Map<String, Channel> channelMap = fetchChannelsInBatch(channelIds);
            if (channelMap == null || channelMap.isEmpty()) {
                logger.info("Nothing to enrich.");
                return;
            }

            for (ContentSource source : batch) {
                Channel channel = channelMap.get(source.sourceIdentifier);
                if (channel != null) {
                    updateSourceFromChannel(source, channel);
                    logger.info(successLog, source.name, source.sourceIdentifier);
                } else {
                    logger.warn(failureLog, source.sourceIdentifier);
                }
            }
        } catch (IOException e) {
            logger.error("API call failed for batch of channel IDs: {}. Marking all as failed.", channelIds, e);
            batch.forEach(source -> logger.warn(failureLog, source.sourceIdentifier));
        }
    }

    private Map<String, Channel> fetchChannelsInBatch(List<String> channelIds) throws IOException {
        List<Channel> items = youtubeDataApiClient.fetchChannels(channelIds);
        return items.stream().collect(Collectors.toMap(Channel::getId, channel -> channel));
    }

    private void updateSourceFromChannel(ContentSource source, Channel channel) {
        ContentSourceDetailService.ContentSourceDetails details = extractContentSourceDetails(channel);
        source.name = details.name();
        source.description = details.description();
        source.subscriberCount = details.subscriberCount();
    }

    private ContentSourceDetailService.ContentSourceDetails extractContentSourceDetails(Channel channel) {
        String name = channel.getSnippet().getTitle();
        String description = channel.getSnippet().getDescription();
        int subscriberCount = 0;
        if (channel.getStatistics() != null && channel.getStatistics().getSubscriberCount() != null) {
            subscriberCount = channel.getStatistics().getSubscriberCount().intValue();
        }
        return new ContentSourceDetailService.ContentSourceDetails(name, description, subscriberCount);
    }
}
