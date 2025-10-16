package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.SourceTypeEnum;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

@ApplicationScoped
@SourceStrategy(SourceTypeEnum.YOUTUBE)
public class YouTubeFetchingStrategy implements ContentFetchingStrategy {

    private static final Logger logger = LoggerFactory.getLogger(YouTubeFetchingStrategy.class);

    private final YouTubeContentFetcherService youTubeContentFetcherService;

    @Inject
    public YouTubeFetchingStrategy(YouTubeContentFetcherService youTubeContentFetcherService) {
        this.youTubeContentFetcherService = youTubeContentFetcherService;
    }

    @Override
    public List<ContentItem> fetch(ContentSource source, LocalDate fetchDate, Long maxTotal, String stopAtVideoId) {
        try {
            LocalDate actualFetchDate = (fetchDate != null) ? fetchDate : LocalDate.now();
            return youTubeContentFetcherService.fetchYouTubeVideos(source, actualFetchDate, maxTotal, stopAtVideoId).stream()
                    .map(ContentItem.class::cast)
                    .toList();
        } catch (IOException e) {
            logger.error("Failed to fetch YouTube videos for content source: {}", source.sourceIdentifier, e);
            return Collections.emptyList();
        }
    }
}
