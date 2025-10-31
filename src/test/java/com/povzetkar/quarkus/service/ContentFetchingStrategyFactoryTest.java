package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.SourceTypeEnum;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    void testInit_throwsExceptionIfDefaultStrategyIsMissing() {
        Instance<ContentFetchingStrategy> emptyInstance = mock(Instance.class);
        when(emptyInstance.handles()).thenReturn(Collections.emptySet());

        ContentFetchingStrategyFactory factoryWithNoStrategies = new ContentFetchingStrategyFactory(emptyInstance);

        assertThrows(IllegalStateException.class, factoryWithNoStrategies::init,
                "Should throw IllegalStateException when no default strategy is provided");
    }
}
