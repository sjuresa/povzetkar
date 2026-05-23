package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.api.dto.PaginatedResponse;
import com.povzetkar.quarkus.api.dto.UserDTO;
import com.povzetkar.quarkus.api.filter.Enveloped;
import com.povzetkar.quarkus.api.mapper.UserMapper;
import com.povzetkar.quarkus.api.util.QueryParser;
import com.povzetkar.quarkus.model.AccountStatus;
import com.povzetkar.quarkus.model.User;
import com.povzetkar.quarkus.service.UserAccountService;
import com.povzetkar.quarkus.service.UserContextService;
import io.quarkus.panache.common.Sort;
import com.povzetkar.quarkus.security.Permission;
import io.quarkus.security.Authenticated;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import com.povzetkar.quarkus.api.filter.OptimisticLocking;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Path("/api/v1/users")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Enveloped
@Tag(name = "Users", description = "Endpoints for User management")
public class UserApiResource extends BaseApiResource {

    private static final String USER_MUST_BE_AUTHENTICATED = "User must be authenticated";

    private final UserMapper userMapper;
    private final UserAccountService userAccountService;

    @Inject
    public UserApiResource(UserMapper userMapper, UserContextService userContextService, UserAccountService userAccountService) {
        super(userContextService);
        this.userMapper = userMapper;
        this.userAccountService = userAccountService;
    }

    @GET
    @Path("/me")
    @Authenticated
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
    @RolesAllowed(Permission.V_USER_MGMT_VIEW)
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
            @QueryParam("size") @DefaultValue("20") int pageSize,
            @QueryParam("accountStatus") AccountStatus accountStatus,
            @QueryParam("email") String email) {

        final int clampedPageSize = Math.clamp(pageSize, 1, ApiConstants.MAX_PAGE_SIZE);

        StringBuilder predicate = new StringBuilder();
        StringBuilder aliasedPredicate = new StringBuilder();
        Map<String, Object> params = new LinkedHashMap<>();

        if (accountStatus != null) {
            predicate.append("accountStatus = :accountStatus");
            aliasedPredicate.append("u.accountStatus = :accountStatus");
            params.put("accountStatus", accountStatus);
        }
        if (email != null && !email.isBlank()) {
            if (!predicate.isEmpty()) { predicate.append(" AND "); aliasedPredicate.append(" AND "); }
            predicate.append("LOWER(email) LIKE LOWER(CONCAT('%', :email, '%')) ESCAPE '\\'");
            aliasedPredicate.append("LOWER(u.email) LIKE LOWER(CONCAT('%', :email, '%')) ESCAPE '\\'");
            params.put("email", email.strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"));
        }

        String whereClause = aliasedPredicate.isEmpty() ? "" : "WHERE " + aliasedPredicate;
        Sort panacheSort = QueryParser.parseSort(sort, Sort.by("u.email").ascending(),
                Set.of("id", "email", "lastLoginAt", "createdAt", "accountStatus"), "u");

        long totalCount = predicate.isEmpty()
                ? User.count()
                : User.count(predicate.toString(), params);

        List<Long> userIds = (params.isEmpty()
                ? User.find("SELECT u.id FROM User u " + whereClause, panacheSort)
                : User.find("SELECT u.id FROM User u " + whereClause, panacheSort, params))
                .project(Long.class).page(pageIndex, clampedPageSize).list();

        if (userIds.isEmpty()) {
            return new PaginatedResponse<>(List.of(), totalCount, pageIndex, clampedPageSize);
        }

        List<User> usersWithRoles = User.list("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.roles LEFT JOIN FETCH u.identities WHERE u.id IN ?1", userIds);

        Map<Long, User> userMap = usersWithRoles.stream().collect(
                Collectors.toMap(u -> u.id, Function.identity(), (existing, _) -> existing, LinkedHashMap::new)
        );

        List<UserDTO> items = userIds.stream()
                .map(userMap::get)
                .filter(Objects::nonNull)
                .map(userMapper::toDTO)
                .toList();

        return new PaginatedResponse<>(items, totalCount, pageIndex, clampedPageSize);
    }

    @POST
    @Path("/{id}/approve")
    @Consumes(MediaType.WILDCARD)
    @RolesAllowed(Permission.V_USER_MGMT_UPDATE)
    @Transactional
    @OptimisticLocking
    @Operation(summary = "Approve a pending user", description = "Transitions account from PENDING_APPROVAL to ENABLED.")
    @APIResponse(responseCode = "200", description = "User approved", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserDTO.class)))
    public UserDTO approveUser(@PathParam("id") Long id, @HeaderParam("If-Match") String ifMatch) {
        return userMapper.toDTO(userAccountService.approve(id, ifMatch));
    }

    @POST
    @Path("/{id}/reject")
    @Consumes(MediaType.WILDCARD)
    @RolesAllowed(Permission.V_USER_MGMT_UPDATE)
    @Transactional
    @OptimisticLocking
    @Operation(summary = "Reject a pending user", description = "Transitions account from PENDING_APPROVAL to REJECTED.")
    @APIResponse(responseCode = "200", description = "User rejected", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserDTO.class)))
    public UserDTO rejectUser(@PathParam("id") Long id, @HeaderParam("If-Match") String ifMatch) {
        return userMapper.toDTO(userAccountService.reject(id, ifMatch));
    }

    @POST
    @Path("/{id}/disable")
    @Consumes(MediaType.WILDCARD)
    @RolesAllowed(Permission.V_USER_MGMT_UPDATE)
    @Transactional
    @OptimisticLocking
    @Operation(summary = "Disable an enabled user", description = "Transitions account from ENABLED to DISABLED.")
    @APIResponse(responseCode = "200", description = "User disabled", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserDTO.class)))
    public UserDTO disableUser(@PathParam("id") Long id, @HeaderParam("If-Match") String ifMatch) {
        return userMapper.toDTO(userAccountService.disable(id, ifMatch));
    }

    @POST
    @Path("/{id}/enable")
    @Consumes(MediaType.WILDCARD)
    @RolesAllowed(Permission.V_USER_MGMT_UPDATE)
    @Transactional
    @OptimisticLocking
    @Operation(summary = "Enable a disabled or rejected user", description = "Transitions account from DISABLED or REJECTED to ENABLED.")
    @APIResponse(responseCode = "200", description = "User enabled", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserDTO.class)))
    public UserDTO enableUser(@PathParam("id") Long id, @HeaderParam("If-Match") String ifMatch) {
        return userMapper.toDTO(userAccountService.enable(id, ifMatch));
    }
}
