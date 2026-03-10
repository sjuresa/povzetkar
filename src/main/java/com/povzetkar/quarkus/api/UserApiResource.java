package com.povzetkar.quarkus.api;
 
import com.povzetkar.quarkus.api.dto.PaginatedResponse;
import com.povzetkar.quarkus.api.dto.UserDTO;
import com.povzetkar.quarkus.api.dto.UserRoleUpdateDTO;
import com.povzetkar.quarkus.api.dto.UserUpdateDTO;
import com.povzetkar.quarkus.api.filter.Enveloped;
import com.povzetkar.quarkus.api.filter.OptimisticLocking;
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
import jakarta.ws.rs.HeaderParam;
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
@Enveloped
@Tag(name = "Users", description = "Endpoints for User management")
public class UserApiResource extends BaseApiResource {

    private static final String USER_MUST_BE_AUTHENTICATED = "User must be authenticated";
    private static final String ADMIN = "admin";

    private final UserMapper userMapper;

    @Inject
    public UserApiResource(UserMapper userMapper, UserContextService userContextService) {
        super(userContextService);
        this.userMapper = userMapper;
    }

    @GET
    @Path("/me")
    @RolesAllowed("user")
    @Transactional
    @Operation(summary = "Get current user profile", description = "Returns the profile and permissions for the authenticated user.")
    @APIResponse(
            responseCode = "200",
            description = "User profile retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserDTO.class))
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
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaginatedResponse.class))
    )
    public PaginatedResponse<UserDTO> getUsers(
            @QueryParam("sort") List<String> sort,
            @QueryParam("page") @DefaultValue("0") int pageIndex,
            @QueryParam("size") @DefaultValue("20") int pageSize) {
        
        final int clampedPageSize = Math.clamp(pageSize, 1, ApiConstants.MAX_PAGE_SIZE);
        long totalCount = User.count();
        io.quarkus.panache.common.Sort panacheSort = com.povzetkar.quarkus.api.util.QueryParser.parseSort(sort, io.quarkus.panache.common.Sort.by("name").ascending());
        List<User> usersForPage = User.<User>findAll(panacheSort).page(pageIndex, clampedPageSize).list();

        if (usersForPage.isEmpty()) {
            return new PaginatedResponse<>(List.of(), totalCount, pageIndex, clampedPageSize);
        }

        // This is the recommended pattern to work around JPA pagination limitations with joins.
        List<Long> userIds = usersForPage.stream().map(u -> u.id).toList();
        // apply the same manual ordering or let the second query omit it
        // since we already ordered the page, returning users in IDs order is fine
        // HQL doesn't easily support ORDER BY FIELD(id, ...), but this is standard. 
        // Best approach is ordering the final result stream exactly as the usersForPage lists, or just sorting normally.
        // For simplicity we will re-sort the final list or keep as is.
        List<User> usersWithRoles = User.list("FROM User u LEFT JOIN FETCH u.roles WHERE u.id IN ?1", userIds);
        
        // ensure we preserve the pagination ordering by mapping from the correctly ordered userIds
        java.util.Map<Long, User> userMap = usersWithRoles.stream().collect(
                java.util.stream.Collectors.toMap(u -> u.id, java.util.function.Function.identity(), (u1, u2) -> u1, java.util.LinkedHashMap::new)
        );
        
        List<UserDTO> items = usersForPage.stream()
                .map(u -> userMap.get(u.id))
                .map(userMapper::toDTO)
                .toList();

        return new PaginatedResponse<>(items, totalCount, pageIndex, clampedPageSize);
    }

    @PATCH
    @Path("/{id}")
    @RolesAllowed("USER_MGMT_UPDATE")
    @Transactional
    @Operation(summary = "Update user status", description = "Activate or deactivate a user.")
    @APIResponse(
            responseCode = "200",
            description = "User status updated successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserDTO.class))
    )
    @OptimisticLocking
    public UserDTO updateUserStatus(@PathParam("id") Long id, @HeaderParam("If-Match") String ifMatch, UserUpdateDTO dto) {
        User user = User.<User>find("from User u left join fetch u.roles where u.id = ?1", id)
                .firstResultOptional()
                .orElseThrow(() -> new NotFoundException("User not found"));

        User currentUser = userContextService.getCurrentUserWithRoles()
                .orElseThrow(() -> new NotAuthorizedException(USER_MUST_BE_AUTHENTICATED));
        
        checkOptimisticLock(user, ifMatch);

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
    @APIResponse(
            responseCode = "200",
            description = "User roles updated successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserDTO.class))
    )
    @OptimisticLocking
    public UserDTO updateUserRoles(@PathParam("id") Long id, @HeaderParam("If-Match") String ifMatch, UserRoleUpdateDTO dto) {
        User user = User.<User>find("from User u left join fetch u.roles where u.id = ?1", id)
                .firstResultOptional()
                .orElseThrow(() -> new NotFoundException("User not found"));

        User currentUser = userContextService.getCurrentUserWithRoles()
                .orElseThrow(() -> new NotAuthorizedException(USER_MUST_BE_AUTHENTICATED));
                
        checkOptimisticLock(user, ifMatch);

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

}
