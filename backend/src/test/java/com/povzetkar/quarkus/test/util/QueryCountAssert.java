package com.povzetkar.quarkus.test.util;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
     * Asserts that exactly the expected number of SQL queries were prepared.
     */
    public void assertCount(int expected) {
        Statistics stats = sessionFactory.getStatistics();
        long actual = stats.getPrepareStatementCount();
        assertEquals(expected, actual, 
            String.format("Performance Regression: Expected %d SQL queries but found %d (N+1 check)", expected, actual));
    }

    /**
     * Asserts that exactly one SQL query was prepared.
     */
    public void assertSingleQuery() {
        assertCount(1);
    }
}
