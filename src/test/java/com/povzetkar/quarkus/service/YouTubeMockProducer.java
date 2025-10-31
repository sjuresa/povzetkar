package com.povzetkar.quarkus.service;

import com.google.api.services.youtube.YouTube;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;
import org.mockito.Mockito;

@ApplicationScoped
public class YouTubeMockProducer {

    public static final YouTube STATIC_MOCK_YOUTUBE = Mockito.mock(YouTube.class, Mockito.RETURNS_DEEP_STUBS);

    @Produces
    @Alternative
    @Priority(1)
    public YouTube produceYouTubeMock() {
        return STATIC_MOCK_YOUTUBE;
    }
}
