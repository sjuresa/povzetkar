package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.service.ContentItemService;
import com.povzetkar.quarkus.service.OrchestrationManager;
import com.povzetkar.quarkus.util.LogKeys;
import com.povzetkar.quarkus.util.StructuredLogger;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

import java.time.LocalDate;

@Path("/api/v1/orchestrator")
public class OrchestratorResource {

    private static final StructuredLogger logger = StructuredLogger.getLogger(OrchestratorResource.class);

    private final OrchestrationManager orchestrationManager;
    private final ContentItemService contentItemService;

    @Inject
    public OrchestratorResource(OrchestrationManager orchestrationManager, ContentItemService contentItemService) {
        this.orchestrationManager = orchestrationManager;
        this.contentItemService = contentItemService;
    }

    @POST
    @Path("/run")
    public Response runOrchestrationManually(@jakarta.validation.constraints.NotNull @jakarta.validation.Valid OrchestrationRequest request) {
        try {
            // The orchestration is intentionally not awaited here.
            // It runs in the background, and we immediately return a response to the client.
            orchestrationManager.runOrchestrationManually(request)
                    .onItem().invoke(() -> logger.info(
                            LogKeys.ACTION, LogKeys.ORCHESTRATION_MANUAL,
                            LogKeys.STATUS, LogKeys.FINISHED,
                            LogKeys.MESSAGE, "Manual orchestration process triggered via API has completed."
                    ))
                    .onFailure().invoke(failure -> logger.error(failure,
                            LogKeys.ACTION, LogKeys.ORCHESTRATION_MANUAL,
                            LogKeys.STATUS, LogKeys.FAILED,
                            LogKeys.MESSAGE, "Manual orchestration process triggered via API failed during execution."
                    ))
                    // We subscribe but intentionally ignore the result. This is a fire-and-forget operation.
                    .subscribeAsCompletionStage();
            return Response.status(Response.Status.ACCEPTED).entity("Orchestration process started.").build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (Exception e) {
            logger.error(e,
                    LogKeys.ACTION, LogKeys.ORCHESTRATION_MANUAL,
                    LogKeys.STATUS, LogKeys.FAILED,
                    LogKeys.MESSAGE, "Error starting manual orchestration",
                    LogKeys.ERROR_MESSAGE, e.getMessage()
            );
            return Response.serverError().entity("Error starting manual orchestration: " + e.getMessage()).build();
        }
    }

    @POST
    @Path("/preview")
    public Uni<Response> runOrchestrationPreview(@jakarta.validation.constraints.NotNull @jakarta.validation.Valid OrchestrationRequest request) {
        LocalDate startDate = request.startDate() != null ? request.startDate() : LocalDate.now();
        LocalDate endDate = request.endDate() != null ? request.endDate() : startDate;

        OrchestrationRequest previewRequest = new OrchestrationRequest(
                startDate,
                endDate,
                false,
                false,
                false,
                true
        );

        return orchestrationManager.runOrchestrationManually(previewRequest)
                .chain(() -> Uni.createFrom().item(() -> contentItemService.countPending(startDate, endDate))
                        .runSubscriptionOn(Infrastructure.getDefaultWorkerPool()))
                .onItem().transform(pendingCount -> {
                    String message = "Preview finished. Found " + pendingCount + " pending items.";
                    return Response.ok(message).build();
                })
                .onFailure().recoverWithItem(failure ->
                        Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                                .entity("Preview failed: " + failure.getMessage())
                                .build()
                );
    }
}
