package com.povzetkar.quarkus.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "content_items")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn
public class ContentItem extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne
    @JoinColumn(name = "content_source_id", nullable = false)
    public ContentSource contentSource;

    @Column(name = "source_url", unique = true, nullable = false)
    public String sourceUrl;

    @Column(name = "raw_content", columnDefinition = "TEXT", nullable = false)
    public String rawContent;

    @Column(name = "title", nullable = false)
    public String title;

    @Column(name = "published_at", nullable = false)
    public Instant publishedAt;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "processing_status", nullable = false)
    public ProcessingStatus processingStatus = ProcessingStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public static void updateProcessingStatus(Long id, ProcessingStatus status) {
        update("processingStatus = ?1 where id = ?2", status, id);
    }
}
