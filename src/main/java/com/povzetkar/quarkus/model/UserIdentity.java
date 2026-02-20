package com.povzetkar.quarkus.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Objects;
import java.util.Optional;

@Entity
@Table(name = "user_identities", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "provider", "external_id" })
})
public class UserIdentity extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonBackReference
    public User user;

    @Column(nullable = false)
    public String provider;

    @Column(name = "external_id", nullable = false)
    public String externalId;

    @Column(nullable = false)
    public String email;

    public static Optional<UserIdentity> findByProviderAndExternalId(String provider, String externalId) {
        return find("from UserIdentity ui join fetch ui.user where ui.provider = ?1 and ui.externalId = ?2", provider,
                externalId).firstResultOptional();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof UserIdentity that))
            return false;
        return Objects.equals(provider, that.provider) && Objects.equals(externalId, that.externalId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, externalId);
    }
}
