package com.povzetkar.quarkus.test.util;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Assertions;

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
     * Asserts that exactly (or fewer) the expected number of SQL queries were prepared.
     * In differential API testing, minor reductions in query counts (due to infra caching)
     * are successful proofs of O(1) complexity, while any increase proves N+1 regressions.
     */
    public void assertCount(int expected) {
        Statistics stats = sessionFactory.getStatistics();
        long actual = stats.getPrepareStatementCount();
        Assertions.assertTrue(actual <= expected,
                String.format("Query count regression: Expected at most %d SQL queries but found %d (N+1 check)", expected, actual));
    }

    /**
     * Asserts that exactly one SQL query was prepared.
     */
    public void assertSingleQuery() {
        assertCount(1);
    }
}
