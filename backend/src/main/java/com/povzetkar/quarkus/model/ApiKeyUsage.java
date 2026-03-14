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

    @Column(name = "error_count", nullable = false)
    private long errorCount;

    public ApiKeyUsage() {
    }

    public ApiKeyUsage(String apiKeyName, LocalDate usageDate) {
        this(apiKeyName, usageDate, 1, 0);
    }

    public ApiKeyUsage(String apiKeyName, LocalDate usageDate, long usageCount, long errorCount) {
        this.apiKeyName = apiKeyName;
        this.usageDate = usageDate;
        this.usageCount = usageCount;
        this.errorCount = errorCount;
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

    public long getErrorCount() {
        return errorCount;
    }

    public void incrementUsageCount() {
        this.usageCount++;
    }

    public void incrementErrorCount() {
        this.errorCount++;
    }
}
