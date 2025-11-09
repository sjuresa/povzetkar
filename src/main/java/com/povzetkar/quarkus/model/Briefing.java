package com.povzetkar.quarkus.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Entity
@Table(name = "briefings")
public class Briefing extends PanacheEntityBase {

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

    @Column(name = "created_at", nullable = false, updatable = false)
    public java.time.Instant createdAt;

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

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = java.time.Instant.now();
        }
    }

    public static Optional<Briefing> findByIdWithAssociations(Long briefingId) {
        return find("select distinct b from Briefing b left join fetch b.summaries s left join fetch s.contentItem where b.id = ?1", briefingId).firstResultOptional();
    }

    public String formattedCreatedAt(String format) {
        if (createdAt == null || format == null || format.isBlank()) {
            return "N/A";
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
        return createdAt.atZone(ZoneId.of("UTC")).format(formatter);
    }

    public String getExecutiveSummary() {
        if (metaSummary == null || metaSummary.isEmpty()) {
            return "";
        }
        Matcher matcher = EXECUTIVE_SUMMARY_PATTERN.matcher(metaSummary);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
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
