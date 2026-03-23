package com.povzetkar.quarkus.api.mapper;

import com.povzetkar.quarkus.api.dto.SummaryDTO;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.YouTubeVideo;
import com.povzetkar.quarkus.model.Summary;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "cdi")
public interface SummaryMapper {

    @Mapping(target = "executiveSummary", expression = "java(entity.getExecutiveSummary())")
    @Mapping(target = "title", source = "contentItem.title")
    @Mapping(target = "sourceUrl", source = "contentItem.sourceUrl")
    @Mapping(target = "publishedAt", source = "contentItem.publishedAt")
    @Mapping(target = "durationSeconds", source = "contentItem", qualifiedByName = "mapDurationSeconds")
    SummaryDTO toDTO(Summary entity);

    @Named("mapDurationSeconds")
    default Long mapDurationSeconds(ContentItem entity) {
        if (entity instanceof YouTubeVideo yt && yt.duration != null) {
            return yt.duration.toSeconds();
        }
        return null;
    }
}
