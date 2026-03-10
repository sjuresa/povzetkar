package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.api.dto.BackgroundJobDTO;
import com.povzetkar.quarkus.api.dto.JobTriggerRequest;
import com.povzetkar.quarkus.api.dto.PaginatedResponse;
import com.povzetkar.quarkus.api.filter.Enveloped;
import com.povzetkar.quarkus.api.mapper.BackgroundJobMapper;
import com.povzetkar.quarkus.model.BackgroundJob;
import com.povzetkar.quarkus.model.JobStatus;
import com.povzetkar.quarkus.service.BackgroundJobService;
import com.povzetkar.quarkus.service.UserContextService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.net.URI;
import java.util.UUID;

@Path("/api/v1/admin")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Enveloped
@Tag(name = "Admin", description = "Endpoints for System Administration and Background Jobs")
public class AdminApiResource extends BaseApiResource {

    private static final String JOBS_PATH = "jobs";

    private final BackgroundJobService backgroundJobService;
    private final BackgroundJobMapper backgroundJobMapper;

    @Inject
    public AdminApiResource(BackgroundJobService backgroundJobService,
                           BackgroundJobMapper backgroundJobMapper,
                           UserContextService userContextService) {
        super(userContextService);
        this.backgroundJobService = backgroundJobService;
        this.backgroundJobMapper = backgroundJobMapper;
    }

    @POST
    @Path("/" + JOBS_PATH + "/trigger")
    @RolesAllowed("ADMIN_SYSTEM_TRIGGER")
    @Operation(summary = "Trigger a background job", description = "Asynchronously starts a background job of the specified type.")
    @APIResponse(
            responseCode = "202",
            description = "Job triggered successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = BackgroundJobDTO.class))
    )
    public Response triggerJob(JobTriggerRequest request, @Context UriInfo uriInfo) {
        BackgroundJob job = backgroundJobService.triggerJob(request.jobType());
        
        URI location = uriInfo.getBaseUriBuilder()
                .path(AdminApiResource.class)
                .path(AdminApiResource.class, "getJobById")
                .build(job.id);

        return Response.accepted()
                .location(location)
                .entity(backgroundJobMapper.toDTO(job))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    @GET
    @Path("/" + JOBS_PATH + "/{id}")
    @RolesAllowed("ADMIN_DASHBOARD_VIEW")
    @Operation(summary = "Get job details")
    @APIResponse(
            responseCode = "200",
            description = "Job details retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = BackgroundJobDTO.class))
    )
    @APIResponse(responseCode = "404", description = "Job not found")
    public BackgroundJobDTO getJobById(@PathParam("id") UUID id) {
        return backgroundJobService.getJobById(id)
                .map(backgroundJobMapper::toDTO)
                .orElseThrow(() -> new NotFoundException("Job not found"));
    }

    @GET
    @Path("/" + JOBS_PATH)
    @RolesAllowed("ADMIN_DASHBOARD_VIEW")
    @Operation(summary = "List background jobs", description = "Returns a paginated list of background job history.")
    @APIResponse(
            responseCode = "200",
            description = "List of jobs retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaginatedResponse.class))
    )
    public PaginatedResponse<BackgroundJobDTO> getJobs(
            @QueryParam("status") JobStatus status,
            @QueryParam("page") @DefaultValue("0") int pageIndex,
            @QueryParam("size") @DefaultValue("20") int pageSize) {
        
        final int clampedPageSize = Math.clamp(pageSize, 1, ApiConstants.MAX_PAGE_SIZE);
        return backgroundJobService.getJobList(status, pageIndex, clampedPageSize);
    }
}
