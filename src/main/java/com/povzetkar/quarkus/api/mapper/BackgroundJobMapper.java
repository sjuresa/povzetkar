package com.povzetkar.quarkus.api.mapper;

import com.povzetkar.quarkus.api.dto.BackgroundJobDTO;
import com.povzetkar.quarkus.model.BackgroundJob;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "jakarta-cdi")
public interface BackgroundJobMapper {

    @Mapping(target = "jobId", source = "id")
    @Mapping(target = "jobType", source = "type")
    BackgroundJobDTO toDTO(BackgroundJob entity);
}
