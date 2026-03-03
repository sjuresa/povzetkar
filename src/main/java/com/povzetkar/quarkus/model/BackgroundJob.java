package com.povzetkar.quarkus.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "background_jobs")
public class BackgroundJob extends PanacheEntityBase {

    @Id
    @Column(name = "job_id")
    public UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public JobType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public JobStatus status;

    @Column(name = "started_at")
    public Instant startedAt;

    @Column(name = "ended_at")
    public Instant endedAt;

    @Column(name = "error_message", length = 2000)
    public String errorMessage;

    @org.hibernate.annotations.CreationTimestamp
    @Column(name = "created_at", updatable = false)
    public Instant createdAt;

    @org.hibernate.annotations.UpdateTimestamp
    @Column(name = "updated_at")
    public Instant updatedAt;

    public BackgroundJob() {
        this.id = UUID.randomUUID();
        this.status = JobStatus.QUEUED;
    }

    public BackgroundJob(JobType type) {
        this();
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BackgroundJob that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
