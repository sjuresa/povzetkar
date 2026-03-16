package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.api.dto.SseEventDTO;
import com.povzetkar.quarkus.model.JobStatus;
import com.povzetkar.quarkus.model.JobType;
import com.povzetkar.quarkus.security.Permission;
import com.povzetkar.quarkus.service.EventBroadcaster;
import com.povzetkar.quarkus.service.UserContextService;
import com.povzetkar.quarkus.service.event.JobStatusChangedEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.UUID;

@Path("/api/v1/events")
@ApplicationScoped
@Tag(name = "Events", description = "Real-time event streams")
public class EventApiResource extends BaseApiResource {

    private static final Logger logger = Logger.getLogger(EventApiResource.class);
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

    @POST
    @Path("/test-pulse")
    @RolesAllowed(Permission.ROLE_USER)
    @Operation(summary = "Trigger test pulse", description = "Simulates a mock job status change event for testing purposes.")
    public Response testPulse() {
        UUID jobId = UUID.randomUUID();
        
        // 1. Send RUNNING event immediately
        eventBroadcaster.broadcast(SseEventDTO.of("JOB_STATUS_CHANGED", 
            new JobStatusChangedEvent(jobId, JobStatus.RUNNING, JobType.GENERATE_TOPIC_BRIEFING)));
        
        // Broadcast a COMPLETED event after 2 seconds to simulate job finishing
        Infrastructure.getDefaultWorkerPool().execute(() -> {
            Uni.createFrom().item(jobId)
               .onItem().delayIt().by(Duration.ofSeconds(2))
               .subscribe().with(
                   id -> {
                       JobStatusChangedEvent completedEvent = new JobStatusChangedEvent(id, JobStatus.COMPLETED, JobType.GENERATE_TOPIC_BRIEFING);
                       eventBroadcaster.broadcast(SseEventDTO.of("JOB_STATUS_CHANGED", completedEvent));
                   },
                   failure -> logger.error("Failed to broadcast test pulse completion", failure)
               );
        });

        return Response.ok().build();
    }
}
