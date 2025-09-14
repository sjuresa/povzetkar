package com.povzetkar.quarkus.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "content_items")
public class ContentItem extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne
    @JoinColumn(name = "channel_id", nullable = false)
    public Channel channel;

    @Column(name = "source_url", unique = true, nullable = false)
    public String sourceUrl;

    @Column(name = "raw_content", columnDefinition = "TEXT", nullable = false)
    public String rawContent;

    @Column(name = "published_at", nullable = false)
    public Instant publishedAt;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "processing_status", nullable = false)
    public ProcessingStatus processingStatus = ProcessingStatus.PENDING;

    @Column(name = "duration_seconds")
    public Integer durationSeconds;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
