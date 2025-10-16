package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.SourceTypeEnum;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@QuarkusTest
class ContentFetchingStrategyFactoryTest {

    @Inject
    ContentFetchingStrategyFactory factory;

    @Test
    void getStrategy_whenYoutubeSource_thenReturnsYoutubeStrategy() {
        ContentFetchingStrategy strategy = factory.getStrategy(SourceTypeEnum.YOUTUBE.getId());
        assertInstanceOf(YouTubeFetchingStrategy.class, strategy);
    }

    @Test
    void getStrategy_whenUnknownSource_thenReturnsNullStrategy() {
        ContentFetchingStrategy strategy = factory.getStrategy(999);
        assertInstanceOf(NullContentFetchingStrategy.class, strategy);
    }
}
