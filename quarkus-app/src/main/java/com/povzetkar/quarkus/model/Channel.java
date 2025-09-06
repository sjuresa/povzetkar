package com.povzetkar.quarkus.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "channels", uniqueConstraints = { @UniqueConstraint(columnNames = {"source_channel_id", "source_type_id"}) })
public class Channel extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "source_channel_id", nullable = false)
    public String sourceChannelId;

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

    @ManyToMany(mappedBy = "channels")
    @JsonBackReference
    public Set<Topic> topics = new HashSet<>();

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
