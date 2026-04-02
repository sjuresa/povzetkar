package com.povzetkar.quarkus.api.mapper;

import com.povzetkar.quarkus.api.dto.BriefingDetailDTO;
import com.povzetkar.quarkus.api.dto.BriefingItemDTO;
import com.povzetkar.quarkus.api.dto.BriefingSummaryDTO;
import com.povzetkar.quarkus.model.Briefing;
import jakarta.inject.Inject;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Comparator;
import java.util.List;
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
    @Mapping(target = "metaSummary", source = "metaSummary")
    @Mapping(target = "summaries", source = "entity", qualifiedByName = "mapToBriefingItems")
    public abstract BriefingDetailDTO toDetailDTO(Briefing entity);

    @Named("mapToBriefingItems")
    protected List<BriefingItemDTO> mapToBriefingItems(Briefing entity) {
        if (entity.summaries == null) {
            return List.of();
        }
        return entity.summaries.stream()
                .sorted(Comparator.comparing(s -> s.id))
                .map(s -> new BriefingItemDTO(
                        contentItemMapper.toDTO(s.contentItem),
                        s.summaryText,
                        s.uuid
                ))
                .collect(Collectors.toList());
    }
}
