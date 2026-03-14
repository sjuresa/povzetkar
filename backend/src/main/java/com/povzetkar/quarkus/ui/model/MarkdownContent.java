package com.povzetkar.quarkus.ui.model;

import io.quarkus.qute.TemplateData;

@TemplateData
public record MarkdownContent(String html, String errorMessage, String warningMessage) {
    public static MarkdownContent success(String html) {
        return new MarkdownContent(html, null, null);
    }

    public static MarkdownContent error(String errorMessage) {
        return new MarkdownContent(null, errorMessage, null);
    }

    public static MarkdownContent warning(String warningMessage) {
        return new MarkdownContent(null, null, warningMessage);
    }
}
