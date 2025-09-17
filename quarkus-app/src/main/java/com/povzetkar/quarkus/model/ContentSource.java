package com.povzetkar.quarkus.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "content_sources", uniqueConstraints = { @UniqueConstraint(columnNames = {"source_identifier", "source_type_id"}) })
public class ContentSource extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "source_identifier", nullable = false)
    public String sourceIdentifier;

    @Column(name = "source_type_id", nullable = false)
    public int sourceTypeId;

    @Column(nullable = false)
    public String name;

    @Column(columnDefinition = "TEXT")
    public String description;

    @Column(name = "subscriber_count", nullable = false)
    public int subscriberCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @ManyToMany(mappedBy = "contentSources")
    @JsonBackReference
    public Set<Topic> topics = new HashSet<>();

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
