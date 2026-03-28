package com.povzetkar.quarkus.api.mapper;

import com.povzetkar.quarkus.api.dto.TopicCreateDTO;
import com.povzetkar.quarkus.api.dto.TopicDTO;
import com.povzetkar.quarkus.api.dto.TopicPatchDTO;
import com.povzetkar.quarkus.api.dto.TopicUpdateDTO;
import com.povzetkar.quarkus.model.Topic;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "jakarta", uses = {ContentSourceMapper.class}, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface TopicMapper {

    @Mapping(target = "contentSources", source = "contentSources")
    TopicDTO toDTO(Topic entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "briefings", ignore = true)
    @Mapping(target = "lastRunDate", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "contentSources", ignore = true)
    @Mapping(target = "enabled", ignore = true)
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

}
