package com.povzetkar.quarkus.api.mapper;

import com.povzetkar.quarkus.api.dto.ContentSourceCreateDTO;
import com.povzetkar.quarkus.api.dto.ContentSourceDTO;
import com.povzetkar.quarkus.api.dto.ContentSourcePatchDTO;
import com.povzetkar.quarkus.api.dto.ContentSourceUpdateDTO;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.SourceTypeEnum;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "cdi", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface ContentSourceMapper {

    @Mapping(target = "platform", expression = "java(mapSourceTypeIdToPlatform(entity.sourceTypeId))")
    @Mapping(target = "sourceUrl", expression = "java(computeSourceUrl(entity))")
    ContentSourceDTO toDTO(ContentSource entity);

    @Mapping(target = "sourceTypeId", expression = "java(mapPlatformToSourceTypeId(dto.platform()))")
    ContentSource toEntity(ContentSourceCreateDTO dto);

    @Mapping(target = "sourceTypeId", expression = "java(mapPlatformToSourceTypeId(dto.platform()))")
    void updateEntity(ContentSourceUpdateDTO dto, @MappingTarget ContentSource entity);

    void patchEntity(ContentSourcePatchDTO dto, @MappingTarget ContentSource entity);

    default String mapSourceTypeIdToPlatform(Integer sourceTypeId) {
        if (sourceTypeId == null)
            return null;
        return SourceTypeEnum.fromId(sourceTypeId).name();
    }

    default Integer mapPlatformToSourceTypeId(String platform) {
        if (platform == null)
            return null;
        try {
            return SourceTypeEnum.valueOf(platform).getId();
        } catch (IllegalArgumentException _) {
            throw new IllegalArgumentException("Invalid platform: " + platform);
        }
    }

    default String computeSourceUrl(ContentSource entity) {
        String platform = mapSourceTypeIdToPlatform(entity.sourceTypeId);
        if ("YOUTUBE".equals(platform) && entity.sourceIdentifier != null) {
            return "https://youtube.com/channel/" + entity.sourceIdentifier;
        } else if ("BLOG".equals(platform)) {
            return entity.sourceIdentifier;
        }
        return null;
    }
}
