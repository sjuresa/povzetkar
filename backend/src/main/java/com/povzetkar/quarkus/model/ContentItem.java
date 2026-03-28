package com.povzetkar.quarkus.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Entity
@Table(name = "content_items")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn
public class ContentItem extends BaseAuditableEntity {

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

    @Column(name = "error_code")
    public String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    public String errorMessage;

    public static void updateProcessingStatus(Long id, ProcessingStatus status) {
        update("processingStatus = ?1 where id = ?2", status, id);
    }

    public static Optional<ContentItem> findByIdWithAssociations(Long id) {
        return find("FROM ContentItem c LEFT JOIN FETCH c.contentSource cs LEFT JOIN FETCH cs.topics WHERE c.id = ?1", id)
                .firstResultOptional();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContentItem that = (ContentItem) o;
        return Objects.equals(sourceUrl, that.sourceUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceUrl);
    }
}
