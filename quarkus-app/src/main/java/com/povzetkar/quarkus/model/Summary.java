package com.povzetkar.quarkus.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Entity
@Table(name = "summaries")
public class Summary extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Lob
    @Column(name = "summary_text", nullable = false)
    public String summaryText;

    @ManyToOne
    @JoinColumn(name = "content_item_id", nullable = false)
    public ContentItem contentItem;

    @Column(name = "created_at", nullable = false, updatable = false)
    public java.time.Instant createdAt;

    // Default constructor for JPA
    public Summary() {
    }

    public Summary(String summaryText, ContentItem contentItem) {
        this.summaryText = summaryText;
        this.contentItem = contentItem;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = java.time.Instant.now();
        }
    }

    public static Summary findByContentId(Long id){
        return find("content_item_id", id).firstResult();
    }

    public static List<Summary> findByTopicAndDateRange(Topic topic, LocalDate date) {
        // Convert LocalDate to Instant range for comparison with createdAt
        java.time.Instant startOfDay = date.atStartOfDay().toInstant(ZoneOffset.UTC);
        java.time.Instant endOfDay = date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        return find("contentItem.contentSource.contentSourceTopics.topic = ?1 and createdAt >= ?2 and createdAt < ?3",
                topic, startOfDay, endOfDay).list();
    }
}
