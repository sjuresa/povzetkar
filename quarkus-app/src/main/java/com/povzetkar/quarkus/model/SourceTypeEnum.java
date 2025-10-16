package com.povzetkar.quarkus.model;

import java.util.Arrays;

public enum SourceTypeEnum {
    YOUTUBE(1),
    BLOG(2),
    DEFAULT(-1);

    private final int id;

    SourceTypeEnum(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static SourceTypeEnum fromId(int id) {
        return Arrays.stream(values())
                .filter(sourceType -> sourceType.id == id)
                .findFirst()
                .orElse(DEFAULT);
    }
}
