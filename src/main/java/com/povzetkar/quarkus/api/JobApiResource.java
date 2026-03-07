package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.api.dto.BackgroundJobDTO;
import com.povzetkar.quarkus.api.mapper.BackgroundJobMapper;
import com.povzetkar.quarkus.model.BackgroundJob;
import com.povzetkar.quarkus.model.User;
import com.povzetkar.quarkus.service.BackgroundJobService;
import com.povzetkar.quarkus.service.UserContextService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.UUID;

@Path("/api/v1/jobs")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Jobs", description = "Endpoints for user tracking background jobs")
public class JobApiResource {

    private final BackgroundJobService backgroundJobService;
    private final BackgroundJobMapper backgroundJobMapper;
    private final UserContextService userContextService;

    @Inject
    public JobApiResource(BackgroundJobService backgroundJobService,
                          BackgroundJobMapper backgroundJobMapper,
                          UserContextService userContextService) {
        this.backgroundJobService = backgroundJobService;
        this.backgroundJobMapper = backgroundJobMapper;
        this.userContextService = userContextService;
    }

    @GET
    @Path("/{id}")
    @RolesAllowed("user")
    @Operation(summary = "Get user job details", description = "Get job details for jobs initiated by the current user.")
    public BackgroundJobDTO getJobById(@PathParam("id") UUID id) {
        User currentUser = userContextService.getCurrentUser()
                .orElseThrow(() -> new NotAuthorizedException("User must be authenticated"));

        BackgroundJob job = backgroundJobService.getJobById(id)
                .orElseThrow(() -> new NotFoundException("Job not found"));

        if (!currentUser.id.equals(job.userId)) {
            throw new ForbiddenException("You do not have permission to view this job.");
        }

        return backgroundJobMapper.toDTO(job);
    }
}
