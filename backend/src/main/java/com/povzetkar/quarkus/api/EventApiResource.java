package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.api.dto.SseEventDTO;
import com.povzetkar.quarkus.service.EventBroadcaster;
import com.povzetkar.quarkus.service.UserContextService;
import io.smallrye.mutiny.Multi;
import com.povzetkar.quarkus.security.Permission;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/v1/events")
@ApplicationScoped
@Tag(name = "Events", description = "Real-time event streams")
public class EventApiResource extends BaseApiResource {

    private final EventBroadcaster eventBroadcaster;

    @Inject
    public EventApiResource(EventBroadcaster eventBroadcaster, UserContextService userContextService) {
        super(userContextService);
        this.eventBroadcaster = eventBroadcaster;
    }

    @GET
    @Path("/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RolesAllowed(Permission.ROLE_USER)
    @Operation(summary = "Real-time event stream", description = "Get a real-time stream of system events (job changes, pulses).")
    public Multi<OutboundSseEvent> stream(@Context Sse sse) {
        return eventBroadcaster.getEventStream()
                .map(event -> sse.newEventBuilder()
                        .name(event.type())
                        .data(SseEventDTO.class, event)
                        .mediaType(MediaType.APPLICATION_JSON_TYPE)
                        .build());
    }
}
