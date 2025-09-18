package com.povzetkar.quarkus.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "briefings")
public class Briefing extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String title;

    @Lob
    @Column(name = "meta_summary")
    public String metaSummary;

    @Column(name = "briefing_date")
    public LocalDate briefingDate;

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    @ManyToMany
    @JoinTable(name = "briefing_summaries",
               joinColumns = @JoinColumn(name = "briefing_id"),
               inverseJoinColumns = @JoinColumn(name = "summary_id"))
    public Set<Summary> summaries = new HashSet<>();

    // Default constructor for JPA
    public Briefing() {
    }

    public Briefing(String title, String metaSummary, LocalDate briefingDate, LocalDateTime createdAt) {
        this.title = title;
        this.metaSummary = metaSummary;
        this.briefingDate = briefingDate;
        this.createdAt = createdAt;
    }
}
