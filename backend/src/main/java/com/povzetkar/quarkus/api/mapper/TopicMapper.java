package com.povzetkar.quarkus.api.mapper;

import com.povzetkar.quarkus.api.dto.TopicCreateDTO;
import com.povzetkar.quarkus.api.dto.TopicDTO;
import com.povzetkar.quarkus.api.dto.TopicPatchDTO;
import com.povzetkar.quarkus.api.dto.TopicUpdateDTO;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.Topic;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "cdi", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface TopicMapper {

    @Mapping(target = "contentSourceIds", source = "contentSources")
    TopicDTO toDTO(Topic entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "briefings", ignore = true)
    @Mapping(target = "lastRunDate", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "contentSources", ignore = true) // Handled manually in resource for now or with custom mapping
    Topic toEntity(TopicCreateDTO dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "briefings", ignore = true)
    @Mapping(target = "lastRunDate", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "contentSources", ignore = true)
    void updateEntity(TopicUpdateDTO dto, @MappingTarget Topic entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "briefings", ignore = true)
    @Mapping(target = "lastRunDate", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "contentSources", ignore = true)
    void patchEntity(TopicPatchDTO dto, @MappingTarget Topic entity);

    default Set<Long> mapSourcesToIds(Set<ContentSource> sources) {
        if (sources == null) return null;
        return sources.stream().map(s -> s.id).collect(Collectors.toSet());
    }
}
