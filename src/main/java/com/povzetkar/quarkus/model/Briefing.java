package com.povzetkar.quarkus.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.povzetkar.quarkus.ui.util.TextUtils;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Entity
@Table(name = "briefings")
public class Briefing extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    @JsonBackReference
    public Topic topic;

    @Column(nullable = false)
    public String title;

    @Column(name = "meta_summary", nullable = false)
    public String metaSummary;

    @Column(name = "briefing_date", nullable = false)
    public LocalDate briefingDate;

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
        return find("select distinct b from Briefing b left join fetch b.summaries s left join fetch s.contentItem where b.id = ?1", briefingId).firstResultOptional();
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
