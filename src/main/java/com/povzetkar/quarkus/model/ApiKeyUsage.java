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
    public Long id;

    @Column(name = "api_key_name", nullable = false)
    public String apiKeyName;

    @Column(name = "usage_date", nullable = false)
    public LocalDate usageDate;

    @Column(name = "usage_count", nullable = false)
    private long usageCount; // Made private

    public ApiKeyUsage() {
    }

    public ApiKeyUsage(String apiKeyName, LocalDate usageDate) {
        this.apiKeyName = apiKeyName;
        this.usageDate = usageDate;
        this.usageCount = 0;
    }

    // Getter for usageCount
    public long getUsageCount() {
        return usageCount;
    }

    // Method to increment usageCount
    public void incrementUsageCount() {
        this.usageCount++;
    }
}
