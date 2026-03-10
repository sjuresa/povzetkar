package com.povzetkar.quarkus.api.util;

import java.util.HashMap;
import java.util.Map;

public class FilterBuilder {

    private final StringBuilder query = new StringBuilder();
    private final Map<String, Object> params = new HashMap<>();

    public FilterBuilder(String initialQuery) {
        if (initialQuery != null && !initialQuery.isBlank()) {
            query.append(initialQuery);
        }
    }

    public FilterBuilder withParam(String name, Object value) {
        if (value != null) {
            params.put(name, value);
        }
        return this;
    }

    public FilterBuilder and(String condition, String paramName, Object paramValue) {
        if (paramValue != null) {
            if (!query.isEmpty()) {
                query.append(" and ");
            }
            query.append(condition);
            params.put(paramName, paramValue);
        }
        return this;
    }

    public String getQuery() {
        return query.toString();
    }

    public Map<String, Object> getParams() {
        return params;
    }
}
