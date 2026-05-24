package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.api.dto.AdminMetricsDTO;
import com.povzetkar.quarkus.api.dto.AuditLogEntryDTO;
import com.povzetkar.quarkus.api.dto.BackgroundJobDTO;
import com.povzetkar.quarkus.api.dto.JobExecutionAuditDTO;
import com.povzetkar.quarkus.api.dto.JobTriggerRequest;
import com.povzetkar.quarkus.api.dto.PaginatedResponse;
import com.povzetkar.quarkus.api.filter.Enveloped;
import com.povzetkar.quarkus.api.filter.Idempotent;
import com.povzetkar.quarkus.api.mapper.BackgroundJobMapper;
import com.povzetkar.quarkus.api.util.FilterBuilder;
import com.povzetkar.quarkus.api.util.QueryParser;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import com.povzetkar.quarkus.model.AuditLog;
import com.povzetkar.quarkus.model.BackgroundJob;
import com.povzetkar.quarkus.model.JobStatus;
import com.povzetkar.quarkus.model.JobType;
import com.povzetkar.quarkus.model.User;
import com.povzetkar.quarkus.security.Permission;
import com.povzetkar.quarkus.service.BackgroundJobService;
import com.povzetkar.quarkus.service.UserContextService;
import io.quarkus.panache.common.Sort;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
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
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.net.URI;
import java.util.List;
import java.util.Set;
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
    private final EntityManager em;

    public static class AuditLogPaginatedResponse extends PaginatedResponse<AuditLogEntryDTO> {
        public AuditLogPaginatedResponse(List<AuditLogEntryDTO> items, long totalCount, int pageIndex, int pageSize) {
            super(items, totalCount, pageIndex, pageSize);
        }
    }

    public static class BackgroundJobPaginatedResponse extends PaginatedResponse<BackgroundJobDTO> {
        public BackgroundJobPaginatedResponse(List<BackgroundJobDTO> items, long totalCount, int pageIndex, int pageSize) {
            super(items, totalCount, pageIndex, pageSize);
        }
    }

    @Inject
    public AdminApiResource(BackgroundJobService backgroundJobService,
                           BackgroundJobMapper backgroundJobMapper,
                           UserContextService userContextService,
                           EntityManager em) {
        super(userContextService);
        this.backgroundJobService = backgroundJobService;
        this.backgroundJobMapper = backgroundJobMapper;
        this.em = em;
    }

    @POST
    @Path("/" + JOBS_PATH + "/trigger")
    @RolesAllowed(Permission.V_ADMIN_SYSTEM_TRIGGER)
    @Idempotent
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
    @RolesAllowed(Permission.V_ADMIN_DASHBOARD_VIEW)
    @Operation(summary = "Get job details")
    @APIResponse(
            responseCode = "200",
            description = "Job details retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = BackgroundJobDTO.class))
    )
    @APIResponse(responseCode = "404", description = "Job not found")
    @Parameter(name = "id", in = ParameterIn.PATH, required = true, description = "The ID of the background job to retrieve")
    public BackgroundJobDTO getJobById(@PathParam("id") UUID id) {
        return backgroundJobService.getJobById(id)
                .map(backgroundJobMapper::toDTO)
                .orElseThrow(() -> new NotFoundException("Job not found"));
    }

    @GET
    @Path("/" + JOBS_PATH)
    @RolesAllowed(Permission.V_ADMIN_DASHBOARD_VIEW)
    @Operation(summary = "List background jobs", description = "Returns a paginated list of background job history.")
    @APIResponse(
            responseCode = "200",
            description = "List of jobs retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = BackgroundJobPaginatedResponse.class))
    )
    public PaginatedResponse<BackgroundJobDTO> getJobs(
            @QueryParam("status") JobStatus status,
            @QueryParam("jobType") JobType jobType,
            @QueryParam("sort") List<String> sort,
            @QueryParam("page") @DefaultValue("0") int pageIndex,
            @QueryParam("size") @DefaultValue("20") int pageSize) {

        final int clampedPageSize = Math.clamp(pageSize, 1, ApiConstants.MAX_PAGE_SIZE);
        Sort panacheSort = QueryParser.parseSort(sort, Sort.by("startedAt").descending(), Set.of("id", "type", "status", "startedAt", "endedAt"));
        return backgroundJobService.getJobList(status, jobType, panacheSort, pageIndex, clampedPageSize);
    }

    @POST
    @Path("/" + JOBS_PATH + "/{id}/restart")
    @Idempotent
    @RolesAllowed(Permission.V_ADMIN_JOB_UPDATE)
    @Operation(summary = "Restart a failed job", description = "Creates a new job using the original job's configuration. Only allowed for FAILED jobs.")
    @APIResponse(responseCode = "202", description = "New job created",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = BackgroundJobDTO.class)))
    @APIResponse(responseCode = "400", description = "Job is not in FAILED state or is an item-level job")
    @APIResponse(responseCode = "404", description = "Job not found")
    @Parameter(name = "id", in = ParameterIn.PATH, required = true, description = "The ID of the FAILED job to restart")
    public Response restartJob(@PathParam("id") UUID id) {
        User admin = getAuthenticatedUser();
        BackgroundJob newJob = backgroundJobService.restartJob(id, admin.id);
        return Response.accepted()
                .entity(backgroundJobMapper.toDTO(newJob))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    @GET
    @Path("/audit-log")
    @RolesAllowed(Permission.V_ADMIN_AUDIT_LOG_VIEW)
    @Operation(summary = "List audit log entries", description = "Returns a paginated list of API request audit log entries, sorted by most recent first.")
    @APIResponse(
            responseCode = "200",
            description = "Audit log entries retrieved successfully",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = AuditLogPaginatedResponse.class))
    )
    @APIResponse(responseCode = "403", description = "Forbidden")
    public PaginatedResponse<AuditLogEntryDTO> getAuditLog(
            @QueryParam("page") @DefaultValue("0") int pageIndex,
            @QueryParam("size") @DefaultValue("20") int pageSize,
            @QueryParam("userId") Long userId,
            @QueryParam("method") String httpMethod,
            @QueryParam("status") Integer responseStatus,
            @QueryParam("correlationId") String correlationId) {
        
        final int clampedPageSize = Math.clamp(pageSize, 1, ApiConstants.MAX_PAGE_SIZE);
        
        FilterBuilder filters = new FilterBuilder("");
        filters.and("userId = :userId", "userId", userId);
        filters.and("httpMethod = :method", "method", httpMethod);
        filters.and("responseStatus = :status", "status", responseStatus);

        if (correlationId != null && !correlationId.isBlank()) {
            filters.and("lower(correlationId) LIKE lower(:correlationId)", "correlationId", "%" + correlationId + "%");
        }

        String query = filters.getQuery();
        Sort sort = Sort.by("createdAt").descending();

        PanacheQuery<AuditLog> panacheQuery = query.isEmpty()
                ? AuditLog.findAll(sort)
                : AuditLog.find(query, sort, filters.getParams());

        long totalCount = query.isEmpty() ? AuditLog.count() : AuditLog.count(query, filters.getParams());

        List<AuditLogEntryDTO> items = panacheQuery
                .page(pageIndex, clampedPageSize)
                .stream()
                .map(AuditLogEntryDTO::fromEntity)
                .toList();
        
        return new PaginatedResponse<>(items, totalCount, pageIndex, clampedPageSize);
    }

    @GET
    @Path("/metrics")
    @RolesAllowed(Permission.V_ADMIN_DASHBOARD_VIEW)
    @Operation(summary = "Get system metrics", description = "Returns live aggregate counts for system health monitoring.")
    @APIResponse(
            responseCode = "200",
            description = "Metrics retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminMetricsDTO.class))
    )
    @APIResponse(responseCode = "403", description = "Forbidden")
    public AdminMetricsDTO getMetrics() {
        Object[] result = (Object[]) em.createNativeQuery("""
                        SELECT
                                (SELECT COUNT(*) FROM background_jobs),
                                (SELECT COUNT(*) FROM background_jobs WHERE status = :runningStatus),
                                (SELECT COUNT(*) FROM background_jobs WHERE status = :failedStatus),
                                (SELECT COUNT(*) FROM topics),
                                (SELECT COUNT(*) FROM topics WHERE enabled = true),
                                (SELECT COUNT(*) FROM content_items),
                                (SELECT COUNT(*) FROM idempotent_requests)""")
                .setParameter("runningStatus", JobStatus.RUNNING.name()).setParameter("failedStatus", JobStatus.FAILED.name()).getSingleResult();

        return new AdminMetricsDTO(
                ((Number) result[0]).longValue(),
                ((Number) result[1]).longValue(),
                ((Number) result[2]).longValue(),
                ((Number) result[3]).longValue(),
                ((Number) result[4]).longValue(),
                ((Number) result[5]).longValue(),
                ((Number) result[6]).longValue()
        );
    }

    @GET
    @Path("/" + JOBS_PATH + "/{id}/audit")
    @RolesAllowed(Permission.V_ADMIN_DASHBOARD_VIEW)
    @Operation(summary = "Get job audit history (Admin)", description = "Get full chronological execution history for any job and its sub-items.")
    public List<JobExecutionAuditDTO> getJobAudit(@PathParam("id") UUID id) {
        BackgroundJob job = backgroundJobService.getJobById(id)
                .orElseThrow(() -> new NotFoundException("Job not found"));

        return backgroundJobService.getJobAuditTrail(id, job.correlationId)
                .stream()
                .map(JobExecutionAuditDTO::fromEntity)
                .toList();
    }
}
