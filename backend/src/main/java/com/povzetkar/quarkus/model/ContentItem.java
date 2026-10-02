package com.povzetkar.quarkus.model;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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

    @Column(name = "author_name")
    public String authorName;

    @Column(name = "published_at", nullable = false)
    public Instant publishedAt;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "processing_status", nullable = false)
    public ProcessingStatus processingStatus = ProcessingStatus.PENDING;

    @Column(name = "error_code")
    public String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    public String errorMessage;

    /** Optional filters of the item list; {@code null} fields do not filter. */
    public record Filter(Long sourceId, List<ProcessingStatus> statuses, Long topicId) {
    }

    /**
     * HQL condition "the item at {@code itemAlias} is visible to {@code :viewer}" (POV-219): its source is in
     * one of the viewer's topics, or its summary is in one of the viewer's briefings — so a briefing stays
     * fully usable after its source was removed from the topic.
     */
    static String visibleToViewer(String itemAlias) {
        return "(" + ContentSource.visibleToViewer(itemAlias + ".contentSource")
                + " or exists (select 1 from Briefing vb join vb.summaries vbs where vbs.contentItem = " + itemAlias
                + " and vb.topic.user = :viewer))";
    }

    /**
     * Ids of the items visible in the scope that match the filter. Page over these ids and load the page with
     * {@link #listByIdsWithAssociations} (see the Hibernate 7.4 note there). The item alias is {@code c}.
     */
    public static PanacheQuery<Long> findVisibleIds(ContentScope scope, Filter filter, Sort sort) {
        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        if (!scope.isUnrestricted()) {
            conditions.add(visibleToViewer("c"));
            params.put("viewer", scope.viewer());
        }
        if (filter.sourceId() != null) {
            conditions.add("c.contentSource.id = :sourceId");
            params.put("sourceId", filter.sourceId());
        }
        if (filter.statuses() != null && !filter.statuses().isEmpty()) {
            conditions.add("c.processingStatus IN :statuses");
            params.put("statuses", filter.statuses());
        }
        if (filter.topicId() != null) {
            conditions.add("EXISTS (SELECT t FROM c.contentSource.topics t WHERE t.id = :topicId)");
            params.put("topicId", filter.topicId());
        }
        String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" and ", conditions);
        return find("SELECT c.id FROM ContentItem c" + where, sort, params).project(Long.class);
    }

    /** Ids of all items of the source (item alias {@code c}); the caller has checked the source is visible. */
    public static PanacheQuery<Long> findIdsBySource(ContentSource source, Sort sort) {
        return find("SELECT c.id FROM ContentItem c WHERE c.contentSource = ?1", sort, source).project(Long.class);
    }

    /** Like {@link #findByIdWithAssociations}, but empty when the item is not visible in the scope. */
    public static Optional<ContentItem> findVisibleByIdWithAssociations(Long id, ContentScope scope) {
        if (scope.isUnrestricted()) {
            return findByIdWithAssociations(id);
        }
        // singleResult for the same Hibernate 7.4 reason as findByIdWithAssociations
        return find("FROM ContentItem c LEFT JOIN FETCH c.contentSource cs LEFT JOIN FETCH cs.topics WHERE c.id = :id and "
                        + visibleToViewer("c"),
                Map.of("id", id, "viewer", scope.viewer()))
                .singleResultOptional();
    }

    /**
     * HQL from {@code ContentItem ci} joined to its topics {@code t}: items still to be (re)processed —
     * {@code PENDING} or {@code RETRY_LATER}, published on the UTC day {@code [:start, :end)}, and due: a video
     * whose automatic transcript retry is scheduled later is left for a later run (POV-222).
     */
    private static final String DUE_FOR_PROCESSING = "select distinct ci from ContentItem ci "
            + "join ci.contentSource cs join cs.topics t "
            + "where ci.processingStatus in :statuses and ci.publishedAt >= :start and ci.publishedAt < :end "
            + "and (ci.nextTranscriptAttemptAt is null or ci.nextTranscriptAttemptAt <= :now)";

    private static Map<String, Object> dueParams(LocalDate publishedOn, Instant now) {
        Map<String, Object> params = new HashMap<>();
        params.put("statuses", Set.of(ProcessingStatus.RETRY_LATER, ProcessingStatus.PENDING));
        params.put("start", publishedOn.atStartOfDay(ZoneOffset.UTC).toInstant());
        params.put("end", publishedOn.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
        params.put("now", now);
        return params;
    }

    /**
     * Items due for (re)processing in the enabled topics — all, or only {@code topicId} when set — with an id above
     * {@code afterId}, ordered by id. Page by keyset (pass the last id of the previous page): processed items leave
     * the selection during a run, so offset paging would skip items.
     */
    public static PanacheQuery<ContentItem> findDueInEnabledTopics(LocalDate publishedOn, Long topicId, Instant now, long afterId) {
        Map<String, Object> params = dueParams(publishedOn, now);
        String query = DUE_FOR_PROCESSING + " and t.enabled = true and ci.id > :afterId";
        params.put("afterId", afterId);
        if (topicId != null) {
            query += " and t.id = :topicId";
            params.put("topicId", topicId);
        }
        return find(query + " order by ci.id", params);
    }

    /** Items due for (re)processing in the topic, whether it is enabled or not (the caller chose the topic). */
    public static List<ContentItem> listDueInTopic(Long topicId, LocalDate publishedOn, Instant now) {
        Map<String, Object> params = dueParams(publishedOn, now);
        params.put("topicId", topicId);
        return list(DUE_FOR_PROCESSING + " and t.id = :topicId", params);
    }

    public static void updateProcessingStatus(Long id, ProcessingStatus status) {
        update("processingStatus = ?1 where id = ?2", status, id);
    }

    public static Optional<ContentItem> findByIdWithAssociations(Long id) {
        // singleResult, not firstResult: a limit on this collection fetch makes Hibernate 7.4 page in a
        // derived table that omits the subclass columns of this SINGLE_TABLE hierarchy (invalid SQL).
        return find("FROM ContentItem c LEFT JOIN FETCH c.contentSource cs LEFT JOIN FETCH cs.topics WHERE c.id = ?1", id)
                .singleResultOptional();
    }

    /**
     * Loads a page of items (ids already paged) with source and topics. Page over ids first and load
     * here — paging this collection fetch directly produces invalid SQL on Hibernate 7.4 (see above).
     * The item alias is {@code c}; qualify sort fields that also exist on source or topic (e.g. {@code c.createdAt}).
     */
    public static List<ContentItem> listByIdsWithAssociations(List<Long> ids, Sort sort) {
        return list("FROM ContentItem c LEFT JOIN FETCH c.contentSource cs LEFT JOIN FETCH cs.topics WHERE c.id IN ?1", sort, ids);
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
