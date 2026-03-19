package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ContentSource;

import java.time.LocalDate;
import java.util.List;

public interface ContentFetchingStrategy {
    List<ContentItem> fetch(ContentSource source, LocalDate fetchDate, Long maxTotal, String stopAtVideoId);
}
