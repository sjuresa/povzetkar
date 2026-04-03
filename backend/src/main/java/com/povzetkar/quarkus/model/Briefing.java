package com.povzetkar.quarkus.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.povzetkar.quarkus.api.dto.BriefingSummaryDTO;
import com.povzetkar.quarkus.ui.util.TextUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Entity
@Table(name = "briefings")
public class Briefing extends BaseAuditableEntity {
    public static final String RECENT_BRIEFINGS_QUERY = """
            select new com.povzetkar.quarkus.api.dto.BriefingSummaryDTO(b.id, b.uuid, b.topic.id, b.topic.name, b.title, b.briefingDate)
            from Briefing b where b.topic.user = ?1 order by b.briefingDate desc""";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    @JsonBackReference
    public Topic topic;

    public static List<BriefingSummaryDTO> findRecentBriefingSummaries(User user, int limit) {
        return getEntityManager().createQuery(RECENT_BRIEFINGS_QUERY, com.povzetkar.quarkus.api.dto.BriefingSummaryDTO.class)
                .setParameter(1, user)
                .setFirstResult(0)
                .setMaxResults(limit)
                .getResultList();
    }

    @Column(nullable = false)
    public String title;

    @Column(name = "meta_summary", nullable = false)
    public String metaSummary;

    @Column(name = "briefing_date", nullable = false)
    public LocalDate briefingDate;

    @Column(unique = true, length = 36)
    public String uuid;

    public void share() {
        if (this.uuid == null) {
            this.uuid = java.util.UUID.randomUUID().toString();
        }
    }

    public void unshare() {
        this.uuid = null;
    }

    @ManyToMany
    @JoinTable(name = "briefing_summaries",
            joinColumns = @JoinColumn(name = "briefing_id"),
            inverseJoinColumns = @JoinColumn(name = "summary_id"))
    public Set<Summary> summaries = new HashSet<>();

    private static final Pattern EXECUTIVE_SUMMARY_PATTERN = Pattern.compile("### 1. Overall Executive Summary\\s*\\n(.*?)\\n\\s*###", Pattern.DOTALL);

    // Default constructor for JPA
    public Briefing() {
    }

    public Briefing(Topic topic, String title, String metaSummary, LocalDate briefingDate) {
        this.topic = topic;
        this.title = title;
        this.metaSummary = metaSummary;
        this.briefingDate = briefingDate;
    }

    public static Optional<Briefing> findByIdWithAssociations(Long briefingId) {
        return find("select distinct b from Briefing b left join fetch b.topic left join fetch b.summaries s left join fetch s.contentItem ci left join fetch ci.contentSource where b.id = ?1", briefingId).firstResultOptional();
    }

    public static Optional<Briefing> findByTopicAndDate(Long topicId, LocalDate briefingDate) {
        return find("topic.id = ?1 and briefingDate = ?2", topicId, briefingDate).firstResultOptional();
    }

    public static Optional<Briefing> findByUuid(String uuid) {
        return find("uuid", uuid).firstResultOptional();
    }

    public static Optional<Briefing> findByUuidWithAssociations(String uuid) {
        return find("select distinct b from Briefing b left join fetch b.topic left join fetch b.summaries s left join fetch s.contentItem ci left join fetch ci.contentSource where b.uuid = ?1", uuid).firstResultOptional();
    }

    public String getExecutiveSummary() {
        return TextUtils.extractSection(metaSummary, EXECUTIVE_SUMMARY_PATTERN);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Briefing briefing = (Briefing) o;
        if (id == null || briefing.id == null) {
            return false;
        }
        return id.equals(briefing.id);
    }
    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
