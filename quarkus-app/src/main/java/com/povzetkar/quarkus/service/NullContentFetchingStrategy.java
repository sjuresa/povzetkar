package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.SourceTypeEnum;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

@ApplicationScoped
@SourceStrategy(SourceTypeEnum.DEFAULT)
public class NullContentFetchingStrategy implements ContentFetchingStrategy {

    private static final Logger logger = LoggerFactory.getLogger(NullContentFetchingStrategy.class);

    @Override
    public List<ContentItem> fetch(ContentSource source, LocalDate fetchDate, Long maxTotal, String stopAtVideoId) {
        logger.warn("No content fetching strategy found for source type ID: {}. Returning empty list.", source.sourceTypeId);
        return Collections.emptyList();
    }
}
