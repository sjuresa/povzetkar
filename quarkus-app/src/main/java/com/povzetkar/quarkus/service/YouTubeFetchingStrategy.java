package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.SourceTypeEnum;
import com.povzetkar.quarkus.model.YouTubeVideo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    @Transactional
    public List<ContentItem> fetch(ContentSource source, LocalDate fetchDate, Long maxTotal, String stopAtVideoId) {
        try {
            LocalDate actualFetchDate = (fetchDate != null) ? fetchDate : LocalDate.now();
            List<YouTubeVideo> fetchedVideos = youTubeContentFetcherService.fetchYouTubeVideos(source, actualFetchDate, maxTotal, stopAtVideoId);

            if (fetchedVideos.isEmpty()) {
                return Collections.emptyList();
            }

            List<String> videoIds = fetchedVideos.stream().map(v -> v.videoId).toList();
            Set<String> existingVideoIds = YouTubeVideo.<YouTubeVideo>list("videoId in ?1", videoIds)
                    .stream()
                    .map(v -> v.videoId).collect(Collectors.toSet());

            List<YouTubeVideo> newVideosToPersist = new ArrayList<>();
            for (YouTubeVideo video : fetchedVideos) {
                if (!existingVideoIds.contains(video.videoId)) {
                    video.contentSource = source;
                    newVideosToPersist.add(video);
                }
            }
            if (!newVideosToPersist.isEmpty()) {
                YouTubeVideo.persist(newVideosToPersist);
            }
            return new ArrayList<>(newVideosToPersist);
        } catch (IOException e) {
            logger.error("Failed to fetch YouTube videos for content source: {}", source.sourceIdentifier, e);
            return Collections.emptyList();
        }
    }
}
