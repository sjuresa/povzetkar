package si.jumis.service;

import com.google.api.services.youtube.model.PlaylistItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import si.jumis.model.Author;
import si.jumis.model.YouTubeVideo;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NewVideoFetcherService {

    private static final Logger logger = LoggerFactory.getLogger(NewVideoFetcherService.class);
    private static final long INITIAL_FETCH_VIDEO_COUNT = 50L;

    private final YouTubeDataService youtubeDataService;
    private final ProcessingStateService stateService;

    public record VideoToProcess(YouTubeVideo video, Author author) {
    }

    public NewVideoFetcherService(YouTubeDataService youtubeDataService, ProcessingStateService stateService) {
        this.youtubeDataService = youtubeDataService;
        this.stateService = stateService;
    }

    public List<VideoToProcess> fetchAllNewVideos(List<Author> authors, LocalDate forDate) {
        List<VideoToProcess> allNewVideos = new ArrayList<>();
        Set<String> processedVideoIds = new HashSet<>();
        for (Author author : authors) {
            try {
                logger.info("--- Checking for new videos from {} ---", author.name());
                String lastVideoId = stateService.findLastProcessedVideoIdBefore(author.channelId(), forDate);
                List<YouTubeVideo> fetchedVideos = fetchVideosForAuthor(author, forDate, lastVideoId);

                logger.info("Found {} new videos for {} in the period.", fetchedVideos.size(), author.name());
                for (YouTubeVideo video : fetchedVideos) {
                    if (processedVideoIds.add(video.id())) {
                        allNewVideos.add(new VideoToProcess(video, author));
                    } else {
                        logger.info("Skipping duplicate video: {} ({})", video.title(), video.id());
                    }
                }

            } catch (IOException e) {
                logger.error("Failed to fetch videos for author '{}'. Skipping this author.", author.name(), e);
            }
        }
        return allNewVideos;
    }

    private List<YouTubeVideo> fetchVideosForAuthor(Author author, LocalDate forDate, String lastVideoId) throws IOException {
        String uploadsPlaylistId = author.uploadsPlaylistId();

        List<PlaylistItem> playlistItems;
        if (lastVideoId == null) {
            logger.info("No previous state found for {} on or before {}. Fetching recent videos to establish initial state.", author.name(), forDate);
            playlistItems = youtubeDataService.fetchPlaylistItems(uploadsPlaylistId, forDate, INITIAL_FETCH_VIDEO_COUNT, null);
        } else {
            logger.info("Found last processed video ID: {}. Fetching all newer videos since then.", lastVideoId);
            playlistItems = youtubeDataService.fetchPlaylistItems(uploadsPlaylistId, forDate, null, lastVideoId);
        }
        List<String> videoIds = playlistItems.stream()
                .map(item -> item.getSnippet().getResourceId().getVideoId())
                .toList();
        Map<String, Duration> videoDurations = youtubeDataService.fetchVideoDurations(videoIds);

        return youtubeDataService.constructYouTubeVideos(playlistItems, videoDurations);
    }
}
