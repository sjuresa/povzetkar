package com.povzetkar.quarkus.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "orchestration_locks")
public class OrchestrationLock extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "orchestration_date", unique = true, nullable = false)
    public LocalDate orchestrationDate;

    @Column(name = "locked_at", nullable = false)
    public Instant lockedAt;

    public OrchestrationLock() {
    }

    public OrchestrationLock(LocalDate orchestrationDate, Instant lockedAt) {
        this.orchestrationDate = orchestrationDate;
        this.lockedAt = lockedAt;
    }
}
