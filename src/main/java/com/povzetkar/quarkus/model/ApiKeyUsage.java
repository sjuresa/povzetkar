package com.povzetkar.quarkus.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;

@Entity
@Table(name = "api_key_usage", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"api_key_name", "usage_date"})
})
public class ApiKeyUsage extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "api_key_name", nullable = false)
    private String apiKeyName;

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Column(name = "usage_count", nullable = false)
    private long usageCount;

    public ApiKeyUsage() {
    }

    public ApiKeyUsage(String apiKeyName, LocalDate usageDate) {
        this.apiKeyName = apiKeyName;
        this.usageDate = usageDate;
        this.usageCount = 1;
    }

    public Long getId() {
        return id;
    }

    public String getApiKeyName() {
        return apiKeyName;
    }

    public LocalDate getUsageDate() {
        return usageDate;
    }

    public long getUsageCount() {
        return usageCount;
    }

    public void incrementUsageCount() {
        this.usageCount++;
    }
}
