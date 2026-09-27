package com.povzetkar.quarkus.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Entity
@Table(name = "users")
public class User extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(unique = true, nullable = false)
    public String username;

    @Column(unique = true, nullable = false)
    @Email
    public String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", length = 50, nullable = false)
    public AccountStatus accountStatus;

    @Column(name = "last_login_at")
    public Instant lastLoginAt;

    @Column(nullable = false)
    public String name;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "role_id"))
    public Set<Role> roles = new HashSet<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    public Set<Topic> topics = new HashSet<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    public Set<UserIdentity> identities = new HashSet<>();

    public void addRole(Role role) {
        this.roles.add(role);
    }

    public void addIdentity(UserIdentity identity) {
        identity.user = this;
        this.identities.add(identity);
    }

    public static List<User> listAllEnabled() {
        return list("accountStatus", AccountStatus.ENABLED);
    }

    public static List<User> findAllWithTopics() {
        return find("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.topics").list();
    }

    /**
     * Google-login lookup. (provider, externalId) is unique, so no limit is needed: singleResult avoids the
     * Hibernate 7.4 derived-table paging of collection fetches (see ContentItem.findByIdWithAssociations).
     */
    public static Optional<User> findByIdentityWithRoles(String provider, String externalId) {
        return find("select u from User u left join fetch u.roles join u.identities i where i.provider = ?1 and i.externalId = ?2",
                provider, externalId).singleResultOptional();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User user)) return false;
        return Objects.equals(username, user.username);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(username);
    }
}
