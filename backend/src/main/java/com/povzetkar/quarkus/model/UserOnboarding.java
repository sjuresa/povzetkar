package com.povzetkar.quarkus.model;

import com.povzetkar.quarkus.api.dto.OnboardingPatchDTO;
import com.povzetkar.quarkus.api.dto.OnboardingStepsPatchDTO;
import com.povzetkar.quarkus.api.exception.BusinessRuleException;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.ws.rs.core.Response;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Per-user state of the dashboard onboarding guide. One row per user, created lazily on first read
 * for an enabled account. Steps are monotonic: once completed they cannot be reset.
 * {@code @DynamicUpdate}: an UPDATE writes only the changed columns, so concurrent patches of
 * different fields (e.g. a step and {@code collapsed}) cannot overwrite each other without locking.
 */
@Entity
@DynamicUpdate
@Table(name = "user_onboarding")
public class UserOnboarding extends PanacheEntityBase {

    @Id
    @Column(name = "user_id")
    public Long userId;

    @Column(name = "step_topic", nullable = false)
    public boolean stepTopic;

    @Column(name = "step_source", nullable = false)
    public boolean stepSource;

    @Column(name = "step_trigger", nullable = false)
    public boolean stepTrigger;

    @Column(name = "step_read", nullable = false)
    public boolean stepRead;

    @Column(name = "step_share", nullable = false)
    public boolean stepShare;

    @Column(nullable = false)
    public boolean dismissed;

    @Column(nullable = false)
    public boolean collapsed;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    /** JPA constructor. */
    protected UserOnboarding() {
    }

    /**
     * Creates the empty guide state of a user. A constructor, not a static factory: PanacheMock mocks
     * every static method of the entity, which would turn a factory into {@code null} in service tests.
     */
    public UserOnboarding(Long userId) {
        this.userId = userId;
    }

    /** Returns the user's guide state, creating the empty row on first access. */
    public static UserOnboarding findOrCreate(Long userId) {
        return UserOnboarding.<UserOnboarding>findByIdOptional(userId)
                .orElseGet(() -> insertIfAbsentAndLoad(userId));
    }

    /**
     * Inserts the empty row unless a concurrent request already did ({@code ON CONFLICT DO NOTHING}
     * instead of a primary-key violation), then loads it.
     */
    static UserOnboarding insertIfAbsentAndLoad(Long userId) {
        getEntityManager()
                .createNativeQuery("INSERT INTO user_onboarding (user_id, updated_at) VALUES (:userId, now()) "
                        + "ON CONFLICT (user_id) DO NOTHING")
                .setParameter("userId", userId)
                .executeUpdate();
        return findById(userId);
    }

    /**
     * Applies a partial update: omitted fields stay unchanged, steps can only be marked complete,
     * {@code dismissed}/{@code collapsed} can be set either way. A patch that tries to reset a step is
     * rejected as a whole, before any field changes.
     */
    public void apply(OnboardingPatchDTO patch) {
        OnboardingStepsPatchDTO steps = patch.steps();
        if (steps != null) {
            if (Stream.of(steps.topic(), steps.source(), steps.trigger(), steps.read(), steps.share())
                    .anyMatch(Boolean.FALSE::equals)) {
                throw new BusinessRuleException(Response.Status.BAD_REQUEST, "ONBOARDING_STEP_IRREVERSIBLE",
                        "Completed onboarding steps cannot be reset");
            }
            stepTopic |= Boolean.TRUE.equals(steps.topic());
            stepSource |= Boolean.TRUE.equals(steps.source());
            stepTrigger |= Boolean.TRUE.equals(steps.trigger());
            stepRead |= Boolean.TRUE.equals(steps.read());
            stepShare |= Boolean.TRUE.equals(steps.share());
        }
        if (patch.dismissed() != null) {
            dismissed = patch.dismissed();
        }
        if (patch.collapsed() != null) {
            collapsed = patch.collapsed();
        }
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserOnboarding that)) return false;
        return userId != null && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(userId);
    }
}
