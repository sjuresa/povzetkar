package com.povzetkar.quarkus.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

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
}
