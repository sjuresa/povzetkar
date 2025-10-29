package com.povzetkar.quarkus.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "summaries")
public class Summary extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "summary_text", nullable = false)
    public String summaryText;

    @OneToOne
    @JoinColumn(name = "content_item_id", nullable = false)
    public ContentItem contentItem;

    @Column(name = "created_at", nullable = false, updatable = false)
    public java.time.Instant createdAt;

    @Column(name = "briefing_date", nullable = false)
    public LocalDate briefingDate;

    // Default constructor for JPA
    public Summary() {
    }

    public Summary(String summaryText, ContentItem contentItem, LocalDate briefingDate) {
        this.summaryText = summaryText;
        this.contentItem = contentItem;
        this.briefingDate = briefingDate;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = java.time.Instant.now();
        }
    }

    public static Summary findByContentId(Long id){
        return find("contentItem.id", id).firstResult();
    }

    public static List<Summary> findByTopicAndDateRange(Topic topic, LocalDate date) {
        return find("from Summary s where s.contentItem.contentSource.id in " +
                        "(select cs.id from ContentSource cs join cs.topics t where t = ?1) and s.briefingDate = ?2 order by s.id",
                topic, date).list();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Summary summary = (Summary) o;
        if (id == null || summary.id == null) {
            return false;
        }
        return id.equals(summary.id);
    }
    @Override
    public int hashCode() {
        // Using a constant for hashCode is a safe approach for JPA entities
        // to avoid issues when an entity transitions from transient to persistent state
        // inside a collection.
        return getClass().hashCode();
    }
}
