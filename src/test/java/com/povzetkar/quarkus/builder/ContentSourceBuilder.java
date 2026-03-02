package com.povzetkar.quarkus.builder;

import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.SourceTypeEnum;

import java.util.UUID;

public class ContentSourceBuilder {

    private Long id;
    private String sourceIdentifier;
    private int sourceTypeId = SourceTypeEnum.YOUTUBE.getId();
    private String name;
    private String description;
    private int subscriberCount = 0;

    private ContentSourceBuilder() {
        // Defaults
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        this.sourceIdentifier = "source_" + uniqueId;
        this.name = "Test Source " + uniqueId;
    }

    public static ContentSourceBuilder aContentSource() {
        return new ContentSourceBuilder();
    }

    public ContentSourceBuilder withId(Long id) {
        this.id = id;
        return this;
    }

    public ContentSourceBuilder withSourceIdentifier(String sourceIdentifier) {
        this.sourceIdentifier = sourceIdentifier;
        return this;
    }

    public ContentSourceBuilder withSourceTypeId(int sourceTypeId) {
        this.sourceTypeId = sourceTypeId;
        return this;
    }

    public ContentSourceBuilder withName(String name) {
        this.name = name;
        return this;
    }

    public ContentSourceBuilder withDescription(String description) {
        this.description = description;
        return this;
    }

    public ContentSourceBuilder withSubscriberCount(int subscriberCount) {
        this.subscriberCount = subscriberCount;
        return this;
    }

    public ContentSource build() {
        ContentSource source = new ContentSource();
        source.id = this.id;
        source.sourceIdentifier = this.sourceIdentifier;
        source.sourceTypeId = this.sourceTypeId;
        source.name = this.name;
        source.description = this.description;
        source.subscriberCount = this.subscriberCount;
        return source;
    }
}
