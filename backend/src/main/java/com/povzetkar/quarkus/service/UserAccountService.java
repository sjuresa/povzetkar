package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.api.exception.BusinessRuleException;
import com.povzetkar.quarkus.model.AccountStatus;
import com.povzetkar.quarkus.model.Role;
import com.povzetkar.quarkus.model.User;
import com.povzetkar.quarkus.security.Permission;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class UserAccountService {

    private final UserContextService userContextService;

    @Inject
    public UserAccountService(UserContextService userContextService) {
        this.userContextService = userContextService;
    }

    @Transactional
    public User approve(Long targetUserId, String ifMatch) {
        User user = loadWithRoles(targetUserId);
        checkOptimisticLock(user, ifMatch);
        assertCurrentStateIs(user, AccountStatus.PENDING_APPROVAL);
        removeRoleByName(user, Permission.ROLE_DEMO);
        addRoleByName(user, Permission.ROLE_USER);
        user.accountStatus = AccountStatus.ENABLED;
        user.persist();
        return user;
    }

    @Transactional
    public User reject(Long targetUserId, String ifMatch) {
        User user = loadWithRoles(targetUserId);
        checkOptimisticLock(user, ifMatch);
        assertCurrentStateIs(user, AccountStatus.PENDING_APPROVAL);
        removeRoleByName(user, Permission.ROLE_DEMO);
        user.accountStatus = AccountStatus.REJECTED;
        user.persist();
        return user;
    }

    @Transactional
    public User disable(Long targetUserId, String ifMatch) {
        User user = loadWithRoles(targetUserId);
        checkOptimisticLock(user, ifMatch);
        assertCurrentStateIs(user, AccountStatus.ENABLED);
        assertNotSelf(user);
        assertNotLastAdmin(user);
        user.accountStatus = AccountStatus.DISABLED;
        user.persist();
        return user;
    }

    @Transactional
    public User enable(Long targetUserId, String ifMatch) {
        User user = loadWithRoles(targetUserId);
        checkOptimisticLock(user, ifMatch);
        if (user.accountStatus != AccountStatus.DISABLED && user.accountStatus != AccountStatus.REJECTED) {
            throw new BusinessRuleException(
                    Response.Status.BAD_REQUEST,
                    "INVALID_STATE_TRANSITION",
                    "Cannot enable user in state: " + user.accountStatus);
        }
        if (user.accountStatus == AccountStatus.REJECTED) {
            addRoleByName(user, Permission.ROLE_USER);
        }
        user.accountStatus = AccountStatus.ENABLED;
        user.persist();
        return user;
    }

    private User loadWithRoles(Long userId) {
        return User.<User>find("SELECT u FROM User u LEFT JOIN FETCH u.roles WHERE u.id = ?1", userId)
                .firstResultOptional()
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));
    }

    private void assertCurrentStateIs(User user, AccountStatus expected) {
        if (user.accountStatus != expected) {
            throw new BusinessRuleException(
                    Response.Status.BAD_REQUEST,
                    "INVALID_STATE_TRANSITION",
                    "Expected account status " + expected + " but was " + user.accountStatus);
        }
    }

    private void assertNotSelf(User user) {
        User currentUser = userContextService.getCurrentUser()
                .orElseThrow(() -> new BusinessRuleException(
                        Response.Status.UNAUTHORIZED,
                        "UNAUTHENTICATED",
                        "No authenticated user found"));
        if (user.id.equals(currentUser.id)) {
            throw new BusinessRuleException(
                    Response.Status.CONFLICT,
                    "CANNOT_SELF_DISABLE",
                    "Cannot disable your own account");
        }
    }

    private void assertNotLastAdmin(User user) {
        boolean isAdmin = user.roles.stream().anyMatch(r -> Permission.ROLE_ADMIN.equalsIgnoreCase(r.name));
        if (!isAdmin) return;
        long enabledAdminCount = User.count(
                "FROM User u JOIN u.roles r WHERE r.name = ?1 AND u.accountStatus = ?2",
                Permission.ROLE_ADMIN, AccountStatus.ENABLED);
        if (enabledAdminCount <= 1) {
            throw new BusinessRuleException(
                    Response.Status.CONFLICT,
                    "LAST_ADMIN_REQUIRED",
                    "Cannot disable the last enabled administrator");
        }
    }

    private void addRoleByName(User user, String roleName) {
        Role role = Role.<Role>find("name", roleName)
                .firstResultOptional()
                .orElseThrow(() -> new IllegalStateException("Required role not found: " + roleName));
        user.roles.add(role);
    }

    private void removeRoleByName(User user, String roleName) {
        user.roles.removeIf(r -> roleName.equalsIgnoreCase(r.name));
    }

    private void checkOptimisticLock(User user, String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new BadRequestException("Missing If-Match header");
        }
        try {
            int clientVersion = Integer.parseInt(ifMatch.replaceFirst("^W/", "").replace("\"", "").trim());
            if (user.version == null || user.version != clientVersion) {
                throw new BusinessRuleException(
                        Response.Status.CONFLICT,
                        "OPTIMISTIC_LOCK_ERROR",
                        "Version mismatch: expected " + user.version + " but got " + clientVersion);
            }
        } catch (NumberFormatException _) {
            throw new BadRequestException("Invalid If-Match header format");
        }
    }
}
