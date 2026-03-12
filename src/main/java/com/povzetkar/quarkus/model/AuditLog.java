package com.povzetkar.quarkus.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a single API request audit record.
 * Persisted by {@code AuditLogFilter} for every request under {@code /api/v1/*}.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "correlation_id", length = 36)
    public String correlationId;

    @Column(name = "user_id")
    public Long userId;

    @Column(name = "http_method", nullable = false, length = 10)
    public String httpMethod;

    @Column(name = "request_path", nullable = false, length = 2048)
    public String requestPath;

    @Column(name = "response_status", nullable = false)
    public Integer responseStatus;

    @Column(name = "duration_ms")
    public Long durationMs;

    @Column(name = "user_agent", length = 512)
    public String userAgent;

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
        if (!(o instanceof AuditLog that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
