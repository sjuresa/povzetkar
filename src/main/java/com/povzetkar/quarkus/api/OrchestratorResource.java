package com.povzetkar.quarkus.api;
 
import com.povzetkar.quarkus.api.dto.ApiEnvelopeDTO;
import com.povzetkar.quarkus.api.dto.ApiErrorDTO;
import com.povzetkar.quarkus.api.filter.Enveloped;
import com.povzetkar.quarkus.service.ContentItemService;
import com.povzetkar.quarkus.service.OrchestrationManager;
import com.povzetkar.quarkus.util.LogKeys;
import com.povzetkar.quarkus.util.StructuredLogger;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
 
import java.time.LocalDate;
 
@Path("/api/v1/orchestrator")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Enveloped
@Tag(name = "Orchestrator", description = "Endpoints for manual system orchestration")
public class OrchestratorResource {

    private static final StructuredLogger logger = StructuredLogger.getLogger(OrchestratorResource.class);
    private static final String MESSAGE = "message";

    private final OrchestrationManager orchestrationManager;
    private final ContentItemService contentItemService;

    @Inject
    public OrchestratorResource(OrchestrationManager orchestrationManager, ContentItemService contentItemService) {
        this.orchestrationManager = orchestrationManager;
        this.contentItemService = contentItemService;
    }

    @POST
    @Path("/run")
    @Operation(summary = "Run orchestration manually", description = "Triggers the full orchestration flow (fetch, summarize, briefing) for a given date range.")
    @APIResponse(responseCode = "202", description = "Orchestration process started")
    @APIResponse(responseCode = "400", description = "Invalid date range or parameters")
    @Enveloped
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
            return Response.status(Response.Status.ACCEPTED)
                    .entity(java.util.Map.of(MESSAGE, "Orchestration process started."))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        } catch (IllegalArgumentException e) {
            var error = new ApiErrorDTO(
                "/errors/bad-request",
                "Bad Request",
                Response.Status.BAD_REQUEST.getStatusCode(),
                e.getMessage(),
                "INVALID_ARGUMENT",
                null
            );
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiEnvelopeDTO.error(error))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        } catch (Exception e) {
            logger.error(e,
                    LogKeys.ACTION, LogKeys.ORCHESTRATION_MANUAL,
                    LogKeys.STATUS, LogKeys.FAILED,
                    LogKeys.MESSAGE, "Error starting manual orchestration",
                    LogKeys.ERROR_MESSAGE, e.getMessage()
            );
            var error = new ApiErrorDTO(
                "/errors/internal-server-error",
                "Internal Server Error",
                Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                "Error starting manual orchestration: " + e.getMessage(),
                "INTERNAL_ERROR",
                null
            );
            return Response.serverError()
                    .entity(ApiEnvelopeDTO.error(error))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }

    @POST
    @Path("/preview")
    @Operation(summary = "Preview orchestration", description = "Analyzes how many items would be processed without actually starting the run.")
    @APIResponse(responseCode = "200", description = "Preview results")
    @Enveloped
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
                    return Response.ok(java.util.Map.of(MESSAGE, message))
                            .type(MediaType.APPLICATION_JSON)
                            .build();
                })
                .onFailure().recoverWithItem(failure -> {
                        var error = new ApiErrorDTO(
                            "/errors/internal-server-error",
                            "Internal Server Error",
                            Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                            "Preview failed: " + failure.getMessage(),
                            "INTERNAL_ERROR",
                            null
                        );
                        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                                .entity(ApiEnvelopeDTO.error(error))
                                .type(MediaType.APPLICATION_JSON)
                                .build();
                });
    }
}
