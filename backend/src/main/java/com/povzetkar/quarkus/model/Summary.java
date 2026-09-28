package com.povzetkar.quarkus.model;

import com.povzetkar.quarkus.ui.util.TextUtils;
import io.quarkus.panache.common.Sort;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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

    public static Optional<Summary> findByUuidWithAssociations(String uuid) {
        return find("FROM Summary s LEFT JOIN FETCH s.contentItem ci LEFT JOIN FETCH ci.contentSource WHERE s.uuid = ?1", uuid).firstResultOptional();
    }

    public static Optional<Summary> findByContentIdWithAssociations(Long contentId) {
        return find("FROM Summary s LEFT JOIN FETCH s.contentItem ci LEFT JOIN FETCH ci.contentSource WHERE ci.id = ?1", contentId).firstResultOptional();
    }

    /** Like {@link #findByContentIdWithAssociations}, but empty when the item is not visible in the scope (POV-219). */
    public static Optional<Summary> findVisibleByContentIdWithAssociations(Long contentId, ContentScope scope) {
        if (scope.isUnrestricted()) {
            return findByContentIdWithAssociations(contentId);
        }
        return find("FROM Summary s LEFT JOIN FETCH s.contentItem ci LEFT JOIN FETCH ci.contentSource WHERE ci.id = :id and "
                        + ContentItem.visibleToViewer("ci"),
                Map.of("id", contentId, "viewer", scope.viewer()))
                .firstResultOptional();
    }

    /** The ids among {@code contentItemIds} that have a summary. */
    public static Set<Long> findContentItemIdsWithSummary(Collection<Long> contentItemIds) {
        if (contentItemIds == null || contentItemIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(getEntityManager()
                .createQuery("SELECT s.contentItem.id FROM Summary s WHERE s.contentItem.id IN :ids", Long.class)
                .setParameter("ids", contentItemIds)
                .getResultList());
    }

    /**
     * Loads a page of summaries (ids already paged) with item, source and topics. Pages over ids first like
     * {@link ContentItem#listByIdsWithAssociations}, for consistency: with {@code Summary} as the root, paging
     * this collection fetch directly would work on Hibernate 7.4 (only a {@code ContentItem} root is affected).
     * The summary alias is {@code s}.
     */
    public static List<Summary> listByIdsWithAssociations(List<Long> ids, Sort sort) {
        return list("SELECT s FROM Summary s JOIN FETCH s.contentItem ci LEFT JOIN FETCH ci.contentSource cs LEFT JOIN FETCH cs.topics WHERE s.id IN ?1",
                sort, ids);
    }

    public static List<Summary> findByTopicAndDateRange(Long topicId, LocalDate date) {
        return find("from Summary s where s.contentItem.contentSource.id in " +
                        "(select cs.id from ContentSource cs join cs.topics t where t.id = ?1) and s.briefingDate = ?2 order by s.id",
                topicId, date).list();
    }

    public static Optional<Summary> findByUuid(String uuid) {
        return find("uuid", uuid).firstResultOptional();
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
