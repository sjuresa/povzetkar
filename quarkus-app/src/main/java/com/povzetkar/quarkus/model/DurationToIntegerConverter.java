package com.povzetkar.quarkus.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.Duration;

@Converter(autoApply = false)
public class DurationToIntegerConverter implements AttributeConverter<Duration, Integer> {

    @Override
    public Integer convertToDatabaseColumn(Duration entityValue) {
        return entityValue == null ? null : (int) entityValue.getSeconds();
    }

    @Override
    public Duration convertToEntityAttribute(Integer databaseValue) {
        return databaseValue == null ? null : Duration.ofSeconds(databaseValue);
    }
}
