package com.povzetkar.quarkus.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "content_sources", uniqueConstraints = { @UniqueConstraint(columnNames = {"source_identifier", "source_type_id"}) })
public class ContentSource extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "source_identifier", nullable = false)
    @NotBlank
    public String sourceIdentifier;

    @Column(name = "source_type_id", nullable = false)
    public int sourceTypeId;

    @Column(nullable = false)
    @NotBlank
    public String name;

    @Column(columnDefinition = "TEXT")
    public String description;

    @Column(name = "subscriber_count", nullable = false)
    public int subscriberCount = 0;

    @ManyToMany(mappedBy = "contentSources")
    @JsonIgnore
    public Set<Topic> topics = new HashSet<>();
}
