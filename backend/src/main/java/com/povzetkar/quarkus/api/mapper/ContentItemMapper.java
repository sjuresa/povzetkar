package com.povzetkar.quarkus.api.mapper;

import com.povzetkar.quarkus.api.dto.ContentItemDTO;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.YouTubeVideo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;


@Mapper(componentModel = "jakarta", uses = {ContentSourceMapper.class})
public interface ContentItemMapper {

    @Mapping(target = "durationSeconds", source = "entity", qualifiedByName = "mapDurationSeconds")
    @Mapping(target = "thumbnailUrl", source = "entity", qualifiedByName = "mapThumbnailUrl")
    @Mapping(target = "contentSource", source = "entity.contentSource")
    @Mapping(target = "topicName", source = "entity", qualifiedByName = "mapTopicName")
    @Mapping(target = "hasTranscript", source = "entity", qualifiedByName = "mapHasTranscript")
    @Mapping(target = "hasSummary", source = "entity", qualifiedByName = "mapHasSummary")
    ContentItemDTO toDTO(ContentItem entity);

    @Named("mapDurationSeconds")
    default Long mapDurationSeconds(ContentItem entity) {
        if (entity instanceof YouTubeVideo yt && yt.duration != null) {
            return yt.duration.toSeconds();
        }
        return null;
    }

    @Named("mapThumbnailUrl")
    default String mapThumbnailUrl(ContentItem entity) {
        if (entity instanceof YouTubeVideo yt && yt.videoId != null) {
            return "https://img.youtube.com/vi/" + yt.videoId + "/hqdefault.jpg";
        }
        return null;
    }

    @Named("mapTopicName")
    default String mapTopicName(ContentItem entity) {
        if (entity.contentSource != null && entity.contentSource.topics != null && !entity.contentSource.topics.isEmpty()) {
            return entity.contentSource.topics.iterator().next().name;
        }
        return "N/A";
    }

    @Named("mapHasTranscript")
    default boolean mapHasTranscript(ContentItem entity) {
        if (entity instanceof YouTubeVideo yt) {
            return yt.transcript != null && !yt.transcript.isBlank();
        }
        return false;
    }

    @Named("mapHasSummary")
    default boolean mapHasSummary(ContentItem entity) {
        // Simple count lookup - since we list ~20 items, this is marginally okay for admin UI
        // In highly performant routes, we'd use a single join query or pre-fetched set.
        return com.povzetkar.quarkus.model.Summary.count("contentItem.id", entity.id) > 0;
    }
}
