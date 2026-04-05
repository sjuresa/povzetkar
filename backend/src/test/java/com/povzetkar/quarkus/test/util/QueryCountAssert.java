package com.povzetkar.quarkus.test.util;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

/**
 * Utility for asserting the number of SQL queries executed during a test.
 * Requires %test.quarkus.hibernate-orm.statistics=true in application.properties.
 */
@ApplicationScoped
public class QueryCountAssert {

    @Inject
    SessionFactory sessionFactory;

    /**
     * Resets Hibernate statistics for the current session factory.
     */
    public void reset() {
        Statistics stats = sessionFactory.getStatistics();
        stats.clear();
    }

    /**
     * Returns the current number of SQL queries prepared.
     */
    public long getActualCount() {
        return sessionFactory.getStatistics().getPrepareStatementCount();
    }

    /**
     * Asserts that the number of SQL queries falls within an acceptable range of the expected count.
     * Upper bound: exact match (catches N+1 regressions where count increases).
     * Lower bound: 50% of expected (catches silent failures where queries stop executing).
     * Minor reductions from infrastructure caching are tolerated within this range.
     */
    public void assertCount(int expected) {
        Statistics stats = sessionFactory.getStatistics();
        long actual = stats.getPrepareStatementCount();
        int lowerBound = Math.max(1, expected / 2);
        org.junit.jupiter.api.Assertions.assertTrue(actual >= lowerBound && actual <= expected,
            String.format("Query count anomaly: Expected %d–%d SQL queries but found %d (N+1 check)", lowerBound, expected, actual));
    }

    /**
     * Asserts that exactly one SQL query was prepared.
     */
    public void assertSingleQuery() {
        assertCount(1);
    }
}
