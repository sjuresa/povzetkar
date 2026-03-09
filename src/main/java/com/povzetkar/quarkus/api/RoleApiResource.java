package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.api.dto.ApiEnvelopeDTO;
import com.povzetkar.quarkus.api.dto.RoleDTO;
import com.povzetkar.quarkus.api.mapper.RoleMapper;
import com.povzetkar.quarkus.model.Role;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/api/v1/roles")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Roles", description = "Endpoints for Role management")
public class RoleApiResource {

    private final RoleMapper roleMapper;

    @Inject
    public RoleApiResource(RoleMapper roleMapper) {
        this.roleMapper = roleMapper;
    }

    @GET
    @RolesAllowed("USER_MGMT_VIEW")
    @Operation(summary = "Get all roles", description = "Retrieves a paginated list of available roles.")
    @APIResponse(
            responseCode = "200",
            description = "List of roles retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = RoleListEnvelope.class))
    )
    public ApiEnvelopeDTO<List<RoleDTO>> getRoles(
            @QueryParam("page") @DefaultValue("0") int pageIndex,
            @QueryParam("size") @DefaultValue("20") int pageSize) {

        final int clampedPageSize = Math.clamp(pageSize, 1, ApiConstants.MAX_PAGE_SIZE);
        PanacheQuery<Role> query = Role.findAll(Sort.by("name").ascending());
        long totalCount = query.count();
        List<RoleDTO> dtoList = query.page(pageIndex, clampedPageSize).list().stream()
                .map(roleMapper::toDTO)
                .toList();
        return ApiEnvelopeDTO.paginated(dtoList, totalCount, pageIndex, clampedPageSize);
    }

    // Helper classes for OpenAPI documentation
    private static class RoleListEnvelope extends ApiEnvelopeDTO<List<RoleDTO>> {}
}
