package com.povzetkar.quarkus.api.mapper;

import com.povzetkar.quarkus.api.dto.ContentItemDTO;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.YouTubeVideo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;


@Mapper(componentModel = "cdi")
public interface ContentItemMapper {

    @Mapping(target = "durationSeconds", source = "entity", qualifiedByName = "mapDurationSeconds")
    @Mapping(target = "thumbnailUrl", source = "entity", qualifiedByName = "mapThumbnailUrl")
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
}
