package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.api.dto.ApiEnvelopeDTO;
import com.povzetkar.quarkus.api.dto.PaginatedResponse;
import com.povzetkar.quarkus.api.dto.UserDTO;
import com.povzetkar.quarkus.api.dto.UserRoleUpdateDTO;
import com.povzetkar.quarkus.api.dto.UserUpdateDTO;
import com.povzetkar.quarkus.api.mapper.UserMapper;
import com.povzetkar.quarkus.model.Role;
import com.povzetkar.quarkus.model.User;
import com.povzetkar.quarkus.service.UserContextService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.HashSet;
import java.util.List;

@Path("/api/v1/users")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Users", description = "Endpoints for User management")
public class UserApiResource {

    private static final String USER_MUST_BE_AUTHENTICATED = "User must be authenticated";
    private static final String ADMIN = "admin";

    private final UserMapper userMapper;
    private final UserContextService userContextService;

    @Inject
    public UserApiResource(UserMapper userMapper, UserContextService userContextService) {
        this.userMapper = userMapper;
        this.userContextService = userContextService;
    }

    @GET
    @Path("/me")
    @RolesAllowed("user")
    @Transactional
    @Operation(summary = "Get current user profile", description = "Returns the profile and permissions for the authenticated user.")
    @APIResponse(
            responseCode = "200",
            description = "User profile retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserEnvelope.class))
    )
    public UserDTO getCurrentUser() {
        User user = userContextService.getCurrentUserWithRoles()
                .orElseThrow(() -> new NotAuthorizedException(USER_MUST_BE_AUTHENTICATED));
        
        return userMapper.toDTO(user);
    }

    @GET
    @RolesAllowed("USER_MGMT_VIEW")
    @Transactional
    @Operation(summary = "List all users", description = "Returns a paginated list of users for administration.")
    @APIResponse(
            responseCode = "200",
            description = "List of users retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserListEnvelope.class))
    )
    public PaginatedResponse<UserDTO> getUsers(
            @QueryParam("page") @DefaultValue("0") int pageIndex,
            @QueryParam("size") @DefaultValue("20") int pageSize) {
        
        pageSize = Math.clamp(pageSize, 1, ApiConstants.MAX_PAGE_SIZE);
        long totalCount = User.count();
        List<User> usersForPage = User.<User>find("FROM User u ORDER BY u.name ASC").page(pageIndex, pageSize).list();

        if (usersForPage.isEmpty()) {
            return new PaginatedResponse<>(List.of(), totalCount);
        }

        // This is the recommended pattern to work around JPA pagination limitations with joins.
        List<Long> userIds = usersForPage.stream().map(u -> u.id).toList();
        List<User> usersWithRoles = User.list("FROM User u LEFT JOIN FETCH u.roles WHERE u.id IN ?1 ORDER BY u.name ASC", userIds);

        List<UserDTO> items = usersWithRoles.stream()
                .map(userMapper::toDTO)
                .toList();

        return new PaginatedResponse<>(items, totalCount);
    }

    @PATCH
    @Path("/{id}")
    @RolesAllowed("USER_MGMT_UPDATE")
    @Transactional
    @Operation(summary = "Update user status", description = "Activate or deactivate a user.")
    public UserDTO updateUserStatus(@PathParam("id") Long id, UserUpdateDTO dto) {
        User user = User.<User>find("from User u left join fetch u.roles where u.id = ?1", id)
                .firstResultOptional()
                .orElseThrow(() -> new NotFoundException("User not found"));

        User currentUser = userContextService.getCurrentUserWithRoles()
                .orElseThrow(() -> new NotAuthorizedException(USER_MUST_BE_AUTHENTICATED));

        boolean userBeingModifiedIsAdmin = user.roles.stream().anyMatch(r -> ADMIN.equalsIgnoreCase(r.name));
        boolean currentUserIsAdmin = currentUser.roles.stream().anyMatch(r -> ADMIN.equalsIgnoreCase(r.name));

        // 1. Protection: Only an administrator can manage another administrator account (activate/deactivate)
        if (userBeingModifiedIsAdmin && !currentUserIsAdmin) {
            throw new WebApplicationException(
                Response.status(Response.Status.FORBIDDEN)
                    .entity("Only an administrator can manage other administrators")
                    .type(MediaType.TEXT_PLAIN)
                    .build());
        }

        if (!dto.active()) {
            // 2. Protection: Admin cannot deactivate themselves
            if (user.id.equals(currentUser.id)) {
                throw new WebApplicationException(
                    Response.status(Response.Status.CONFLICT)
                        .entity("Cannot deactivate yourself")
                        .type(MediaType.TEXT_PLAIN)
                        .build());
            }

            // 3. Protection: Do not allow deactivating the last administrator
            if (isLastAdmin(user)) {
                 throw new WebApplicationException(
                    Response.status(Response.Status.CONFLICT)
                        .entity("Cannot deactivate the last administrator in the system")
                        .type(MediaType.TEXT_PLAIN)
                        .build());
            }
        }

        user.active = dto.active();
        user.persist();

        return userMapper.toDTO(user);
    }

    @PUT
    @Path("/{id}/roles")
    @RolesAllowed("USER_MGMT_UPDATE")
    @Transactional
    @Operation(summary = "Update user roles", description = "Assign or remove roles for a user.")
    public UserDTO updateUserRoles(@PathParam("id") Long id, UserRoleUpdateDTO dto) {
        User user = User.<User>find("from User u left join fetch u.roles where u.id = ?1", id)
                .firstResultOptional()
                .orElseThrow(() -> new NotFoundException("User not found"));

        User currentUser = userContextService.getCurrentUserWithRoles()
                .orElseThrow(() -> new NotAuthorizedException(USER_MUST_BE_AUTHENTICATED));

        List<Long> requestedRoleIds = dto.roleIds() != null ? dto.roleIds() : List.of();
        List<Role> requestedRoles = Role.list("id in ?1", requestedRoleIds);
        if (requestedRoles.size() != new HashSet<>(requestedRoleIds).size()) {
            throw new BadRequestException("One or more invalid role IDs provided.");
        }

        boolean requestContainsAdmin = requestedRoles.stream().anyMatch(r -> ADMIN.equalsIgnoreCase(r.name));
        boolean userBeingModifiedIsAdmin = user.roles.stream().anyMatch(r -> ADMIN.equalsIgnoreCase(r.name));
        boolean currentUserIsAdmin = currentUser.roles.stream().anyMatch(r -> ADMIN.equalsIgnoreCase(r.name));
        boolean isSelf = user.id.equals(currentUser.id);

        // 1. Protection: Admin cannot remove their own admin role
        if (isSelf && userBeingModifiedIsAdmin && !requestContainsAdmin) {
            throw new WebApplicationException(
                Response.status(Response.Status.CONFLICT)
                    .entity("Cannot remove admin role from yourself")
                    .type(MediaType.TEXT_PLAIN)
                    .build());
        }

        // 2. Protection: Only an existing admin can grant or remove the admin role
        if ((requestContainsAdmin || userBeingModifiedIsAdmin) && !currentUserIsAdmin) {
            throw new WebApplicationException(
                Response.status(Response.Status.FORBIDDEN)
                    .entity("Only an administrator can manage the admin role")
                    .type(MediaType.TEXT_PLAIN)
                    .build());
        }

        // 3. Protection: Do not allow removing the last administrator
        if (userBeingModifiedIsAdmin && !requestContainsAdmin && isLastAdmin(user)) {
             throw new WebApplicationException(
                Response.status(Response.Status.CONFLICT)
                    .entity("Cannot remove the last administrator from the system")
                    .type(MediaType.TEXT_PLAIN)
                    .build());
        }

        // Apply changes
        user.roles.clear();
        user.roles.addAll(new HashSet<>(requestedRoles));
        user.persist();

        return userMapper.toDTO(user);
    }


    private boolean isLastAdmin(User userBeingModified) {
        // If the user being modified is an admin
        boolean isAdmin = userBeingModified.roles.stream().anyMatch(r -> ADMIN.equalsIgnoreCase(r.name));
        if (!isAdmin) return false;

        // Count active admins in the system
        long adminCount = User.find("select u from User u join u.roles r where r.name = ?1 and u.active = true", ADMIN).count();
        
        return adminCount <= 1;
    }

    // Helper classes for OpenAPI documentation
    private static class UserEnvelope extends ApiEnvelopeDTO<UserDTO> {}
    private static class UserListEnvelope extends ApiEnvelopeDTO<List<UserDTO>> {}
}
