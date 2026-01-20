package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.service.OrchestrationManager;
import com.povzetkar.quarkus.util.LogKeys;
import com.povzetkar.quarkus.util.StructuredLogger;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

@Path("/api/v1/orchestrator")
public class OrchestratorResource {

    private static final StructuredLogger logger = StructuredLogger.getLogger(OrchestratorResource.class);

    private final OrchestrationManager orchestrationManager;

    @Inject
    public OrchestratorResource(OrchestrationManager orchestrationManager) {
        this.orchestrationManager = orchestrationManager;
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
}
