package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.service.OrchestrationManager;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

@Path("/api/v1/orchestrator")
public class OrchestratorResource {

    private static final Logger logger = LoggerFactory.getLogger(OrchestratorResource.class);

    private final OrchestrationManager orchestrationManager;

    @Inject
    public OrchestratorResource(OrchestrationManager orchestrationManager) {
        this.orchestrationManager = orchestrationManager;
    }

    @POST
    @Path("/run")
    public Response runOrchestrationManually(
            @QueryParam("startDate") String startDateStr,
            @QueryParam("endDate") String endDateStr,
            @QueryParam("processPending") @DefaultValue("false") boolean processPending,
            @QueryParam("summarize") @DefaultValue("false") boolean summarize,
            @QueryParam("createBriefing") @DefaultValue("false") boolean createBriefing) {

        LocalDate finalStartDate;
        LocalDate finalEndDate;
        try {
            finalStartDate = Optional.ofNullable(startDateStr).filter(s -> !s.isBlank()).map(LocalDate::parse).orElse(null);
            finalEndDate = Optional.ofNullable(endDateStr).filter(s -> !s.isBlank()).map(LocalDate::parse).orElse(null);
        } catch (DateTimeParseException _) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid date format. Please use YYYY-MM-DD.").build();
        }

        try {
            // The orchestration is intentionally not awaited here.
            // It runs in the background, and we immediately return a response to the client.
            orchestrationManager.runOrchestrationManually(finalStartDate, finalEndDate, processPending, summarize, createBriefing)
                    .onItem().invoke(() -> logger.info("Manual orchestration process triggered via API has completed."))
                    .onFailure().invoke(failure -> logger.error("Manual orchestration process triggered via API failed during execution.", failure))
                    // We subscribe but intentionally ignore the result. This is a fire-and-forget operation.
                    .subscribeAsCompletionStage();
            return Response.status(Response.Status.ACCEPTED).entity("Orchestration process started.").build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (Exception e) {
            logger.error("Error starting manual orchestration", e);
            return Response.serverError().entity("Error starting manual orchestration: " + e.getMessage()).build();
        }
    }
}
