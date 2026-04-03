package com.povzetkar.quarkus.model;

import com.povzetkar.quarkus.ui.util.TextUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

@Entity
@Table(name = "summaries")
public class Summary extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "uuid", unique = true, length = 36)
    public String uuid;

    @Column(name = "summary_text", nullable = false)
    public String summaryText;

    @OneToOne
    @JoinColumn(name = "content_item_id", nullable = false)
    public ContentItem contentItem;

    @Column(name = "briefing_date", nullable = false)
    public LocalDate briefingDate;

    private static final Pattern EXECUTIVE_SUMMARY_PATTERN = Pattern.compile("### Executive Summary\\s*\\n(.*?)\\n\\s*###", Pattern.DOTALL);

    // Default constructor for JPA
    public Summary() {
    }

    public Summary(String summaryText, ContentItem contentItem, LocalDate briefingDate) {
        this.summaryText = summaryText;
        this.contentItem = contentItem;
        this.briefingDate = briefingDate;
    }

    public void share() {
        if (this.uuid == null) {
            this.uuid = java.util.UUID.randomUUID().toString();
        }
    }

    public void unshare() {
        this.uuid = null;
    }

    public static Summary findByContentId(Long id){
        return find("contentItem.id", id).firstResult();
    }

    public static Optional<Summary> findByIdWithAssociations(Long id) {
        return find("FROM Summary s LEFT JOIN FETCH s.contentItem ci LEFT JOIN FETCH ci.contentSource WHERE s.id = ?1", id).firstResultOptional();
    }

    public static Summary findByContentIdWithAssociations(Long contentId) {
        return find("FROM Summary s LEFT JOIN FETCH s.contentItem ci LEFT JOIN FETCH ci.contentSource WHERE ci.id = ?1", contentId).firstResult();
    }

    public static List<Summary> findByTopicAndDateRange(Long topicId, LocalDate date) {
        return find("from Summary s where s.contentItem.contentSource.id in " +
                        "(select cs.id from ContentSource cs join cs.topics t where t.id = ?1) and s.briefingDate = ?2 order by s.id",
                topicId, date).list();
    }

    public static Optional<Summary> findByUuid(String uuid) {
        return find("uuid", uuid).firstResultOptional();
    }

    public static Optional<Summary> findByUuidWithAssociations(String uuid) {
        return find("FROM Summary s LEFT JOIN FETCH s.contentItem ci LEFT JOIN FETCH ci.contentSource WHERE s.uuid = ?1", uuid).firstResultOptional();
    }

    public String getExecutiveSummary() {
        return TextUtils.extractSection(summaryText, EXECUTIVE_SUMMARY_PATTERN);
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
        return Objects.hashCode(id);
    }
}
