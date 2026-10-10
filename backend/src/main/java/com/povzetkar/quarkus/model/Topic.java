package com.povzetkar.quarkus.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import jakarta.persistence.*;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Entity
@Table(name = "topics", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "user_id", "name" })
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

    @ManyToMany(cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @JoinTable(name = "content_source_topics", joinColumns = @JoinColumn(name = "topic_id"), inverseJoinColumns = @JoinColumn(name = "content_source_id"))
    @JsonManagedReference
    public Set<ContentSource> contentSources = new HashSet<>();

    @Column(nullable = false)
    public boolean enabled = true;

    @Column(name = "last_run_date")
    public java.time.Instant lastRunDate;

    @Column(name = "prompt_template")
    public String promptTemplate;

    public static List<Topic> findEnabledByUsers(List<User> users) {
        return find(
                "select distinct t from Topic t left join fetch t.contentSources where t.enabled = true and t.user in ?1",
                users).list();
    }

    public static Topic findByIdWithSources(Long id) {
        return find("from Topic t left join fetch t.contentSources where t.id = ?1", id).firstResult();
    }

    /** The user's topics with their sources; each topic appears once, so a page holds whole topics. Alias {@code t}. */
    public static PanacheQuery<Topic> findByUserWithSources(User user, Sort sort) {
        return find("select distinct t from Topic t left join fetch t.contentSources where t.user = ?1", sort, user);
    }

    public static Optional<Topic> findByIdAndUserWithSources(Long id, User user) {
        return find("from Topic t left join fetch t.contentSources where t.id = ?1 and t.user = ?2", id, user)
                .firstResultOptional();
    }

    public static Optional<Topic> findByUserAndName(User user, String name) {
        return find("user = ?1 and name = ?2", user, name).firstResultOptional();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Topic topic))
            return false;
        return Objects.equals(user, topic.user) && Objects.equals(name, topic.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(user, name);
    }
}
