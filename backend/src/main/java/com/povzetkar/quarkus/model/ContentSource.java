package com.povzetkar.quarkus.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Entity
@Table(name = "content_sources", uniqueConstraints = { @UniqueConstraint(columnNames = {"source_identifier", "source_type_id"}) })
public class ContentSource extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "source_identifier", nullable = false)
    @NotBlank
    public String sourceIdentifier;

    @Column(name = "source_type_id", nullable = false)
    public int sourceTypeId;

    @Column(nullable = true)
    public String name;

    @Column(columnDefinition = "TEXT")
    public String description;

    @Column(name = "subscriber_count", nullable = false)
    public int subscriberCount = 0;

    @ManyToMany(mappedBy = "contentSources")
    @JsonIgnore
    public Set<Topic> topics = new HashSet<>();

    /**
     * HQL condition "the source at {@code sourcePath} is in one of the viewer's topics" — the single
     * definition of source visibility (POV-219). Binds the named parameter {@code :viewer}.
     */
    static String visibleToViewer(String sourcePath) {
        return "exists (select 1 from Topic vt join vt.contentSources vs where vs = " + sourcePath + " and vt.user = :viewer)";
    }

    /**
     * Sources visible in the scope, ordered by id, optionally filtered by a case-insensitive search over
     * identifier, name and description and without the excluded ids.
     */
    public static PanacheQuery<ContentSource> findVisible(ContentScope scope, String search, Collection<Long> excludeIds) {
        StringBuilder hql = new StringBuilder("from ContentSource s where 1 = 1");
        Map<String, Object> params = new HashMap<>();
        if (!scope.isUnrestricted()) {
            hql.append(" and ").append(visibleToViewer("s"));
            params.put("viewer", scope.viewer());
        }
        if (search != null && !search.isBlank()) {
            hql.append(" and (lower(s.sourceIdentifier) like :search or lower(s.name) like :search or lower(s.description) like :search)");
            params.put("search", "%" + search.toLowerCase() + "%");
        }
        if (excludeIds != null && !excludeIds.isEmpty()) {
            hql.append(" and s.id not in :excludeIds");
            params.put("excludeIds", excludeIds);
        }
        return find(hql.toString(), Sort.by("id"), params);
    }

    public static Optional<ContentSource> findVisibleById(Long id, ContentScope scope) {
        if (scope.isUnrestricted()) {
            return findByIdOptional(id);
        }
        return find("from ContentSource s where s.id = :id and " + visibleToViewer("s"),
                Map.of("id", id, "viewer", scope.viewer()))
                .firstResultOptional();
    }

    /** The sources among {@code ids} that are visible in the scope; invisible or unknown ids are simply absent. */
    public static List<ContentSource> listVisibleByIds(Collection<Long> ids, ContentScope scope) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        if (scope.isUnrestricted()) {
            return list("id in ?1", ids);
        }
        return list("from ContentSource s where s.id in :ids and " + visibleToViewer("s"),
                Map.of("ids", ids, "viewer", scope.viewer()));
    }

    public static Optional<ContentSource> findByIdentifier(String sourceIdentifier, int sourceTypeId) {
        return find("sourceIdentifier = ?1 and sourceTypeId = ?2", sourceIdentifier, sourceTypeId).firstResultOptional();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof ContentSource that))
            return false;
        return sourceTypeId == that.sourceTypeId && Objects.equals(sourceIdentifier, that.sourceIdentifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceIdentifier, sourceTypeId);
    }
}
