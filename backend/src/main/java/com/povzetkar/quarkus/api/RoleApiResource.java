package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.api.dto.PaginatedResponse;
import com.povzetkar.quarkus.api.dto.RoleDTO;
import com.povzetkar.quarkus.api.filter.Enveloped;
import com.povzetkar.quarkus.api.mapper.RoleMapper;
import com.povzetkar.quarkus.api.util.QueryParser;
import com.povzetkar.quarkus.model.Role;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import com.povzetkar.quarkus.security.Permission;
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
import java.util.Set;
 
@Path("/api/v1/roles")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Enveloped
@Tag(name = "Roles", description = "Endpoints for Role management")
public class RoleApiResource {

    private final RoleMapper roleMapper;

    @Inject
    public RoleApiResource(RoleMapper roleMapper) {
        this.roleMapper = roleMapper;
    }

    @GET
    @RolesAllowed(Permission.V_USER_MGMT_VIEW)
    @Operation(summary = "Get all roles", description = "Retrieves a paginated list of available roles.")
    @APIResponse(
            responseCode = "200",
            description = "List of roles retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaginatedResponse.class))
    )
    public PaginatedResponse<RoleDTO> getRoles(
            @QueryParam("sort") List<String> sort,
            @QueryParam("page") @DefaultValue("0") int pageIndex,
            @QueryParam("size") @DefaultValue("20") int pageSize) {

        final int clampedPageSize = Math.clamp(pageSize, 1, ApiConstants.MAX_PAGE_SIZE);
        Sort panacheSort = QueryParser.parseSort(sort, Sort.by("name").ascending(), Set.of("id", "name"));
        PanacheQuery<Role> query = Role.findAll(panacheSort);
        long totalCount = query.count();
        List<RoleDTO> dtoList = query.page(pageIndex, clampedPageSize).stream()
                .map(roleMapper::toDTO)
                .toList();
        return new PaginatedResponse<>(dtoList, totalCount, pageIndex, clampedPageSize);
    }
}
