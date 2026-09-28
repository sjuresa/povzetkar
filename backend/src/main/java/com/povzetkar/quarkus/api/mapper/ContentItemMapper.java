package com.povzetkar.quarkus.api.mapper;

import com.povzetkar.quarkus.api.dto.ContentItemDTO;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ContentScope;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.YouTubeVideo;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Objects;
import java.util.Set;

@Mapper(componentModel = "jakarta", uses = {ContentSourceMapper.class})
public interface ContentItemMapper {

    @Mapping(target = "durationSeconds", source = "entity", qualifiedByName = "mapDurationSeconds")
    @Mapping(target = "formattedDuration", source = "entity", qualifiedByName = "mapFormattedDuration")
    @Mapping(target = "thumbnailUrl", source = "entity", qualifiedByName = "mapThumbnailUrl")
    @Mapping(target = "contentSource", source = "entity.contentSource")
    @Mapping(target = "topicName", source = "entity", qualifiedByName = "mapTopicName")
    @Mapping(target = "hasTranscript", source = "entity", qualifiedByName = "mapHasTranscript")
    @Mapping(target = "hasSummary", expression = "java(itemIdsWithSummaries != null && itemIdsWithSummaries.contains(entity.id))")
    ContentItemDTO toDTO(ContentItem entity, @Context Set<Long> itemIdsWithSummaries, @Context ContentScope scope);

    default ContentItemDTO toDTO(ContentItem entity, @Context ContentScope scope) {
        return toDTO(entity, java.util.Collections.emptySet(), scope);
    }

    @Named("mapDurationSeconds")
    default Long mapDurationSeconds(ContentItem entity) {
        if (entity instanceof YouTubeVideo yt && yt.duration != null) {
            return yt.duration.toSeconds();
        }
        return null;
    }

    @Named("mapFormattedDuration")
    default String mapFormattedDuration(ContentItem entity) {
        if (!(entity instanceof YouTubeVideo yt) || yt.duration == null) return null;
        long h = yt.duration.toHours();
        int m = yt.duration.toMinutesPart();
        int s = yt.duration.toSecondsPart();
        return h > 0
                ? String.format("%d:%02d:%02d", h, m, s)
                : String.format("%d:%02d", m, s);
    }

    @Named("mapThumbnailUrl")
    default String mapThumbnailUrl(ContentItem entity) {
        if (entity instanceof YouTubeVideo yt && yt.videoId != null) {
            return "https://img.youtube.com/vi/" + yt.videoId + "/hqdefault.jpg";
        }
        return null;
    }

    /**
     * The name of the first (lowest id) of the item's topics that is in the viewer's scope — a source can be in
     * other users' topics too, whose names must not leak (POV-219). "N/A" if none of the viewer's topics has it
     * (e.g. an item visible only through a briefing).
     */
    @Named("mapTopicName")
    default String mapTopicName(ContentItem entity, @Context ContentScope scope) {
        if (entity.contentSource == null || entity.contentSource.topics == null) {
            return "N/A";
        }
        return entity.contentSource.topics.stream()
            .filter(topic -> isInScope(topic, scope))
            .min(java.util.Comparator.comparing(topic -> topic.id))
            .map(topic -> topic.name)
            .orElse("N/A");
    }

    private static boolean isInScope(Topic topic, ContentScope scope) {
        return scope.isUnrestricted() || (topic.user != null && Objects.equals(topic.user.id, scope.viewer().id));
    }

    @Named("mapHasTranscript")
    default boolean mapHasTranscript(ContentItem entity) {
        if (entity instanceof YouTubeVideo yt) {
            return yt.transcript != null && !yt.transcript.isBlank();
        }
        return false;
    }
}
