package com.povzetkar.quarkus.model;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum SourceTypeEnum {
    YOUTUBE(1),
    BLOG(2),
    DEFAULT(-1);

    private final int id;

    private static final Map<Integer, SourceTypeEnum> ID_MAP;

    static {
        ID_MAP = Arrays.stream(values())
                .filter(sourceType -> sourceType != DEFAULT)
                .collect(Collectors.toMap(SourceTypeEnum::getId, Function.identity()));
    }

    SourceTypeEnum(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static SourceTypeEnum fromId(int id) {
        return ID_MAP.getOrDefault(id, DEFAULT);
    }
}
