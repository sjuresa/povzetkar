package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.service.OrchestratorService;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Path("/api/v1/orchestrator")
public class OrchestratorResource {

    private static final Logger logger = LoggerFactory.getLogger(OrchestratorResource.class);

    private final OrchestratorService orchestratorService;

    @Inject
    public OrchestratorResource(OrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    @POST
    @Path("/run")
    public Response runOrchestrationManually(
            @QueryParam("startDate") String startDateStr,
            @QueryParam("endDate") String endDateStr) {

        LocalDate startDate = null;
        LocalDate endDate = null;

        try {
            if (startDateStr != null && !startDateStr.isEmpty()) {
                startDate = LocalDate.parse(startDateStr);
            }
            if (endDateStr != null && !endDateStr.isEmpty()) {
                endDate = LocalDate.parse(endDateStr);
            }
        } catch (DateTimeParseException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid date format. Please use YYYY-MM-DD.").build();
        }

        try {
            orchestratorService.runOrchestrationManually(startDate, endDate)
                    .await().indefinitely(); // Block and wait for completion or failure
            return Response.status(Response.Status.ACCEPTED).entity("Orchestration process started.").build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (Exception e) {
            logger.error("Error starting manual orchestration", e);
            return Response.serverError().entity("Error starting manual orchestration: " + e.getMessage()).build();
        }
    }
}
