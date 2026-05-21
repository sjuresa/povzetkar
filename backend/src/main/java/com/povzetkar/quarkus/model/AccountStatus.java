package com.povzetkar.quarkus.model;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Lifecycle state of a user account.
 * Full state machine implemented in POV-185; this stub enables POV-184 compilation.
 */
@Schema(description = "Account activation state")
public enum AccountStatus {
    PENDING_APPROVAL,
    ENABLED,
    DISABLED,
    REJECTED
}
