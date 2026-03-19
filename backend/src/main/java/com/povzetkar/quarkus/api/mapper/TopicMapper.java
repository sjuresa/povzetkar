package com.povzetkar.quarkus.api.mapper;

import com.povzetkar.quarkus.api.dto.TopicCreateDTO;
import com.povzetkar.quarkus.api.dto.TopicDTO;
import com.povzetkar.quarkus.api.dto.TopicPatchDTO;
import com.povzetkar.quarkus.api.dto.TopicUpdateDTO;
import com.povzetkar.quarkus.model.Topic;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "cdi", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface TopicMapper {

    TopicDTO toDTO(Topic entity);

    Topic toEntity(TopicCreateDTO dto);

    void updateEntity(TopicUpdateDTO dto, @MappingTarget Topic entity);

    void patchEntity(TopicPatchDTO dto, @MappingTarget Topic entity);
}
