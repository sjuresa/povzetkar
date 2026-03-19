package com.povzetkar.quarkus.builder;

import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.ProcessingStatus;

import java.time.Instant;

public class ContentItemBuilder {

    private ContentSource contentSource;
    private String sourceUrl;
    private String rawContent;
    private String title;
    private Instant publishedAt;
    private ProcessingStatus processingStatus = ProcessingStatus.COMPLETED;
    private String errorCode;
    private String errorMessage;

    private ContentItemBuilder() {
    }

    public static ContentItemBuilder aContentItem() {
        return new ContentItemBuilder();
    }

    public ContentItemBuilder withContentSource(ContentSource contentSource) {
        this.contentSource = contentSource;
        return this;
    }

    public ContentItemBuilder withSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
        return this;
    }

    public ContentItemBuilder withRawContent(String rawContent) {
        this.rawContent = rawContent;
        return this;
    }

    public ContentItemBuilder withTitle(String title) {
        this.title = title;
        return this;
    }

    public ContentItemBuilder withPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
        return this;
    }

    public ContentItemBuilder withProcessingStatus(ProcessingStatus processingStatus) {
        this.processingStatus = processingStatus;
        return this;
    }

    public ContentItemBuilder withErrorCode(String errorCode) {
        this.errorCode = errorCode;
        return this;
    }

    public ContentItemBuilder withErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        return this;
    }

    public ContentItem build() {
        ContentItem item = new ContentItem();
        item.contentSource = this.contentSource;
        item.sourceUrl = this.sourceUrl != null ? this.sourceUrl : "https://example.com/item-" + java.util.UUID.randomUUID();
        item.rawContent = this.rawContent != null ? this.rawContent : "Default raw content";
        item.title = this.title != null ? this.title : "Default Title";
        item.publishedAt = this.publishedAt != null ? this.publishedAt : Instant.now();
        item.processingStatus = this.processingStatus;
        item.errorCode = this.errorCode;
        item.errorMessage = this.errorMessage;
        return item;
    }
}
