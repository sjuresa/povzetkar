package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.JobExecutionAudit;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class RetentionServiceTest {

    @Inject
    RetentionService retentionService;

    @Inject
    EntityManager entityManager;

    @Inject
    MeterRegistry meterRegistry;

    @BeforeEach
    @Transactional
    void setUp() throws IOException {
        tearDown();
        
        // Locate the seed script relative to the project root (handling different CWDs)
        Path seedPath = Paths.get("src/test/resources/sql/retention-seed.sql");
        if (!Files.exists(seedPath)) {
            // Fallback for monorepo structure if CWD is at root
            seedPath = Paths.get("backend/src/test/resources/sql/retention-seed.sql");
        }
        
        // Execute the seed script statement-by-statement
        String seedSql = Files.readString(seedPath);
        for (String statement : seedSql.split(";")) {
            if (!statement.trim().isEmpty()) {
                entityManager.createNativeQuery(statement).executeUpdate();
            }
        }
    }

    @AfterEach
    @Transactional
    void tearDown() {
        JobExecutionAudit.deleteAll();
    }

    @Test
    void testPurgeOldAuditLogs_DeletesOnlyRequiredRecords() {
        // Initial state check
        assertEquals(15, JobExecutionAudit.count(), "Should have 15 records before purging");
        double metricValueBefore = meterRegistry.counter("povzetkar.maintenance.audits_purged_total").count();

        // Trigger retention (Manually, since it's @Scheduled)
        retentionService.purgeOldAuditLogs();

        // Verification: 5 survivors expected (89-day boundary and newer)
        assertEquals(5, JobExecutionAudit.count(), "Should have exactly 5 records remaining after 90-day purge");

        // Qualitative verification of survivors
        long freshCount = JobExecutionAudit.find("message LIKE 'Fresh%' OR message LIKE 'Boundary%'").count();
        assertEquals(5, freshCount, "All survivors must be the fresh or boundary records");
        
        // Metrics verification
        double metricValueAfter = meterRegistry.counter("povzetkar.maintenance.audits_purged_total").count();
        assertEquals(10.0, metricValueAfter - metricValueBefore, "Prometheus metric should record exactly 10 deletions.");
    }
}
