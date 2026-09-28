package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.api.dto.ContentSourceCreateDTO;
import com.povzetkar.quarkus.api.exception.BusinessRuleException;
import com.povzetkar.quarkus.builder.ContentSourceBuilder;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.SourceTypeEnum;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Source creation with persistence and the platform API mocked (QA rule 3, POV-219): the identifier is
 * normalized to the channel ID before lookup, and only supported, existing channels become sources.
 */
@QuarkusTest
class ContentSourceServiceTest {

    private static final int YOUTUBE = SourceTypeEnum.YOUTUBE.getId();
    private static final String CHANNEL_ID = "UCHnyfMqiRRG1u-2MsSQLbXA";

    @Inject
    ContentSourceService service;

    @InjectMock
    ContentSourceDetailService contentSourceDetailService;

    @InjectMock
    Session session;

    @BeforeEach
    void setUp() {
        PanacheMock.mock(ContentSource.class);
        when(ContentSource.findByIdentifier(anyString(), anyInt())).thenReturn(Optional.empty());
    }

    @Test
    void anExistingChannelIsFoundByItsChannelIdWhateverFormWasEntered() {
        givenChannel("@veritasium", "Veritasium");
        ContentSource existing = ContentSourceBuilder.aContentSource().withId(74L).withSourceIdentifier(CHANNEL_ID).build();
        when(ContentSource.findByIdentifier(CHANNEL_ID, YOUTUBE)).thenReturn(Optional.of(existing));

        ContentSourceService.Result result = service.findOrCreate(new ContentSourceCreateDTO("YOUTUBE", "  @veritasium ", null));

        assertSame(existing, result.source());
        assertFalse(result.created());
        verify(contentSourceDetailService).fetchContentSourceDetails(YOUTUBE, "@veritasium");
        verify(session, never()).persist(any());
    }

    @Test
    void aSourceStoredUnderTheEnteredIdentifierIsReturnedWithoutAskingThePlatform() {
        ContentSource legacy = ContentSourceBuilder.aContentSource().withId(80L).withSourceIdentifier("@legacy").build();
        when(ContentSource.findByIdentifier("@legacy", YOUTUBE)).thenReturn(Optional.of(legacy));

        ContentSourceService.Result result = service.findOrCreate(new ContentSourceCreateDTO("YOUTUBE", " @legacy ", null));

        assertSame(legacy, result.source());
        assertFalse(result.created());
        verifyNoInteractions(contentSourceDetailService);
    }

    @Test
    void anUnavailablePlatformIsServiceUnavailableAndStoresNothing() {
        when(contentSourceDetailService.fetchContentSourceDetails(anyInt(), anyString()))
                .thenThrow(new SourceDetailsUnavailableException("quota", null));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.findOrCreate(new ContentSourceCreateDTO("YOUTUBE", "@busy", null)));

        assertEquals(Response.Status.SERVICE_UNAVAILABLE, ex.status);
        assertEquals("SOURCE_CHECK_UNAVAILABLE", ex.code);
        verify(session, never()).persist(any());
    }

    @Test
    void aNewChannelIsStoredUnderItsChannelIdWithThePlatformDetails() {
        givenChannel("https://youtube.com/@veritasium", "Veritasium");

        ContentSourceService.Result result = service.findOrCreate(
                new ContentSourceCreateDTO("YOUTUBE", "https://youtube.com/@veritasium", "Typed name"));

        assertTrue(result.created());
        ArgumentCaptor<Object> persisted = ArgumentCaptor.forClass(Object.class);
        verify(session).persist(persisted.capture());
        ContentSource source = (ContentSource) persisted.getValue();
        assertSame(source, result.source());
        assertEquals(CHANNEL_ID, source.sourceIdentifier);
        assertEquals(YOUTUBE, source.sourceTypeId);
        assertEquals("Veritasium", source.name);
        assertEquals("Science videos", source.description);
        assertEquals(1000, source.subscriberCount);
    }

    @Test
    void theNameFallsBackToTheEnteredNameThenToTheChannelId() {
        givenChannel("@nameless", " ");

        ContentSource withEnteredName = service.findOrCreate(new ContentSourceCreateDTO("YOUTUBE", "@nameless", "Entered")).source();
        ContentSource withoutName = service.findOrCreate(new ContentSourceCreateDTO("YOUTUBE", "@nameless", null)).source();

        assertEquals("Entered", withEnteredName.name);
        assertEquals(CHANNEL_ID, withoutName.name);
    }

    @Test
    void anUnknownChannelIsABadRequestAndStoresNothing() {
        when(contentSourceDetailService.fetchContentSourceDetails(anyInt(), anyString())).thenReturn(Optional.empty());

        assertBadRequest("SOURCE_NOT_FOUND", new ContentSourceCreateDTO("YOUTUBE", "@nobody", null));

        PanacheMock.verify(ContentSource.class, never()).findByIdentifier(eq(CHANNEL_ID), anyInt());
        verify(session, never()).persist(any());
    }

    @Test
    void blogIsNotSupportedYet() {
        assertBadRequest("PLATFORM_NOT_SUPPORTED", new ContentSourceCreateDTO("BLOG", "https://blog.example", null));

        verifyNoInteractions(contentSourceDetailService);
        verify(session, never()).persist(any());
    }

    @Test
    void anUnknownPlatformIsABadRequest() {
        assertBadRequest("INVALID_PLATFORM", new ContentSourceCreateDTO("MYSPACE", "someone", null));

        verifyNoInteractions(contentSourceDetailService);
    }

    private void givenChannel(String enteredIdentifier, String name) {
        when(contentSourceDetailService.fetchContentSourceDetails(YOUTUBE, enteredIdentifier.trim())).thenReturn(Optional.of(
                new ContentSourceDetailService.ContentSourceDetails(CHANNEL_ID, name, "Science videos", 1000)));
    }

    private void assertBadRequest(String code, ContentSourceCreateDTO dto) {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.findOrCreate(dto));
        assertEquals(Response.Status.BAD_REQUEST, ex.status);
        assertEquals(code, ex.code);
    }
}
