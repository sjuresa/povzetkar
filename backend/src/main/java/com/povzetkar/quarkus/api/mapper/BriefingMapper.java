package com.povzetkar.quarkus.api.mapper;

import com.povzetkar.quarkus.api.dto.BriefingDetailDTO;
import com.povzetkar.quarkus.api.dto.BriefingSummaryDTO;
import com.povzetkar.quarkus.api.dto.ContentItemDTO;
import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.Summary;
import jakarta.inject.Inject;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "jakarta")
public abstract class BriefingMapper {

    @Inject
    ContentItemMapper contentItemMapper;

    @Mapping(target = "topicId", source = "topic.id")
    @Mapping(target = "topicName", source = "topic.name")
    @Mapping(target = "uuid", source = "uuid")
    public abstract BriefingSummaryDTO toSummaryDTO(Briefing entity);

    @Mapping(target = "topicId", source = "topic.id")
    @Mapping(target = "topicName", source = "topic.name")
    @Mapping(target = "uuid", source = "uuid")
    @Mapping(target = "executiveSummary", source = "entity", qualifiedByName = "mapExecutiveSummary")
    @Mapping(target = "briefingText", source = "entity", qualifiedByName = "mapBriefingText")
    @Mapping(target = "sourceContentItems", source = "summaries")
    public abstract BriefingDetailDTO toDetailDTO(Briefing entity);

    @Named("mapExecutiveSummary")
    protected String mapExecutiveSummary(Briefing entity) {
        return entity.metaSummary;
    }

    @Named("mapBriefingText")
    protected String mapBriefingText(Briefing entity) {
        if (entity.summaries == null) {
            return "";
        }
        return entity.summaries.stream()
                .sorted(Comparator.comparing(s -> s.id))
                .map(s -> "\n\n<a id=\"item-" + (s.contentItem != null ? s.contentItem.id : 0) + "\"></a>\n\n" + s.summaryText)
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    protected List<ContentItemDTO> mapSummaries(Set<Summary> summaries) {
        if (summaries == null) {
            return List.of();
        }
        return summaries.stream()
                .sorted(Comparator.comparing(s -> s.id))
                .map(s -> s.contentItem)
                .map(contentItemMapper::toDTO)
                .toList();
    }
}
