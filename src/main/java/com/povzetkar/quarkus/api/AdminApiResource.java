package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.api.dto.BackgroundJobDTO;
import com.povzetkar.quarkus.api.dto.JobTriggerRequest;
import com.povzetkar.quarkus.api.dto.PaginatedResponse;
import com.povzetkar.quarkus.api.mapper.BackgroundJobMapper;
import com.povzetkar.quarkus.model.BackgroundJob;
import com.povzetkar.quarkus.model.JobStatus;
import com.povzetkar.quarkus.service.BackgroundJobService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.net.URI;
import java.util.UUID;

import static com.povzetkar.quarkus.api.ApiConstants.MAX_PAGE_SIZE;

@Path("/api/v1/admin")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Admin", description = "Endpoints for System Administration and Background Jobs")
public class AdminApiResource {

    private static final String JOBS_PATH = "jobs";

    private final BackgroundJobService backgroundJobService;
    private final BackgroundJobMapper backgroundJobMapper;

    @Inject
    public AdminApiResource(BackgroundJobService backgroundJobService,
                           BackgroundJobMapper backgroundJobMapper) {
        this.backgroundJobService = backgroundJobService;
        this.backgroundJobMapper = backgroundJobMapper;
    }

    @POST
    @Path("/" + JOBS_PATH + "/trigger")
    @RolesAllowed("ADMIN_SYSTEM_TRIGGER")
    @Operation(summary = "Trigger a background job", description = "Asynchronously starts a background job of the specified type.")
    @APIResponse(responseCode = "202", description = "Job triggered successfully")
    public Response triggerJob(JobTriggerRequest request, @Context UriInfo uriInfo) {
        BackgroundJob job = backgroundJobService.triggerJob(request.jobType());
        
        URI location = uriInfo.getBaseUriBuilder()
                .path(AdminApiResource.class)
                .path(JOBS_PATH)
                .path(job.id.toString())
                .build();

        return Response.accepted()
                .location(location)
                .entity(backgroundJobMapper.toDTO(job))
                .build();
    }

    @GET
    @Path("/" + JOBS_PATH + "/{id}")
    @RolesAllowed("ADMIN_DASHBOARD_VIEW")
    @Operation(summary = "Get job details")
    public BackgroundJobDTO getJobById(@PathParam("id") UUID id) {
        return backgroundJobService.getJobById(id)
                .map(backgroundJobMapper::toDTO)
                .orElseThrow(() -> new NotFoundException("Job not found"));
    }

    @GET
    @Path("/" + JOBS_PATH)
    @RolesAllowed("ADMIN_DASHBOARD_VIEW")
    @Operation(summary = "List background jobs", description = "Returns a paginated list of background job history.")
    public PaginatedResponse<BackgroundJobDTO> getJobs(
            @QueryParam("status") JobStatus status,
            @QueryParam("page") @DefaultValue("0") int pageIndex,
            @QueryParam("size") @DefaultValue("20") int pageSize) {
        
        // Apply pageSize limits as per CR5 in previous task
        pageSize = Math.clamp(pageSize, 1, MAX_PAGE_SIZE);
        
        return backgroundJobService.getJobList(status, pageIndex, pageSize);
    }
}
