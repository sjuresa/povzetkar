package com.povzetkar.quarkus.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "idempotent_requests", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "user_id", "idempotency_key" })
})
public class IdempotentRequest extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    public User user;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    public String idempotencyKey;

    @Column(name = "request_path", nullable = false, length = 255)
    public String requestPath;

    @Column(name = "request_hash", length = 64)
    public String requestHash;

    @Column(name = "response_body", columnDefinition = "TEXT")
    public String responseBody;

    @Column(name = "response_status")
    public Integer responseStatus;

    @Column(name = "response_location")
    public String responseLocation;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IdempotentRequest that = (IdempotentRequest) o;
        // Using user.id is safer for lazy associations to avoid unintended fetches or exceptions.
        return Objects.equals(idempotencyKey, that.idempotencyKey) &&
               Objects.equals(requestPath, that.requestPath) &&
               user != null && that.user != null && Objects.equals(user.id, that.user.id);
    }

    @Override
    public int hashCode() {
        // Using user.id is safer for lazy associations.
        return Objects.hash(idempotencyKey, requestPath, user != null ? user.id : null);
    }
}
