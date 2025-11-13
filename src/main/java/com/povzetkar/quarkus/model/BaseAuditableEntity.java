package com.povzetkar.quarkus.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@MappedSuperclass
public abstract class BaseAuditableEntity extends PanacheEntityBase {

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public String formattedCreatedAt(String format) {
        if (createdAt == null || format == null || format.isBlank()) {
            return "N/A";
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
        return createdAt.atZone(ZoneId.of("UTC")).format(formatter);
    }
}
