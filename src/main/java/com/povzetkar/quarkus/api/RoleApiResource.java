package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.api.dto.PaginatedResponse;
import com.povzetkar.quarkus.api.dto.RoleDTO;
import com.povzetkar.quarkus.model.Role;
import com.povzetkar.quarkus.api.mapper.RoleMapper;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
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

    private static final int MAX_PAGE_SIZE = 100;

    @GET
    @RolesAllowed("USER_MGMT_VIEW")
    @Operation(summary = "Get all roles", description = "Retrieves a paginated list of available roles.")
    public PaginatedResponse<RoleDTO> getRoles(
            @QueryParam("page") @DefaultValue("0") int pageIndex,
            @QueryParam("size") @DefaultValue("20") int pageSize) {

        pageSize = Math.clamp(pageSize, 1, MAX_PAGE_SIZE);
        PanacheQuery<Role> query = Role.findAll(Sort.by("name").ascending());
        long totalCount = query.count();
        List<RoleDTO> dtoList = query.page(pageIndex, pageSize).list().stream()
                .map(roleMapper::toDTO)
                .toList();
        return new PaginatedResponse<>(dtoList, totalCount);
    }
}
