package com.povzetkar.quarkus.model;

public enum SourceTypeEnum {
    YOUTUBE(1),
    BLOG(2);

    private final int id;

    SourceTypeEnum(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }
}
