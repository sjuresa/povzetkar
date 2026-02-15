package com.povzetkar.quarkus.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Entity
@Table(name = "topics", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "name"})
})
public class Topic extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonBackReference
    public User user;

    @OneToMany(mappedBy = "topic", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    public Set<Briefing> briefings = new HashSet<>();

    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(name = "content_source_topics",
               joinColumns = @JoinColumn(name = "topic_id"),
               inverseJoinColumns = @JoinColumn(name = "content_source_id"))
    @JsonManagedReference
    public Set<ContentSource> contentSources = new HashSet<>();

    @Column(nullable = false)
    public boolean enabled = true;

    public static List<Topic> findEnabledByUsers(List<User> users) {
        return find("select distinct t from Topic t left join fetch t.contentSources where t.enabled = true and t.user in ?1", users).list();
    }

    public static Optional<Topic> findByIdWithContentSources(Long id) {
        return find("SELECT t FROM Topic t LEFT JOIN FETCH t.contentSources WHERE t.id = ?1", id).firstResultOptional();
    }
}
