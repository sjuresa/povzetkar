package com.povzetkar.quarkus.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "idempotent_requests")
public class IdempotentRequest extends PanacheEntityBase {

    @Id
    @Column(name = "idempotency_key", nullable = false, length = 128)
    public String idempotencyKey;

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
}
