package com.povzetkar.quarkus.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "summaries")
public class Summary extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Lob
    @Column(name = "summary_text")
    public String summaryText;

    @ManyToOne
    @JoinColumn(name = "content_item_id")
    public ContentItem contentItem;

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    // Default constructor for JPA
    public Summary() {
    }

    public Summary(String summaryText, ContentItem contentItem, LocalDateTime createdAt) {
        this.summaryText = summaryText;
        this.contentItem = contentItem;
        this.createdAt = createdAt;
    }
}
