package com.povzetkar.quarkus.api.util;

import io.quarkus.panache.common.Sort;
import io.quarkus.panache.common.Sort.Direction;

import java.util.List;

public final class QueryParser {

    private QueryParser() {
        // Utility class
    }

    public static Sort parseSort(List<String> sortParams, Sort defaultSort) {
        if (sortParams == null || sortParams.isEmpty()) {
            return defaultSort;
        }

        Sort sort = null;

        for (String param : sortParams) {
            String[] parts = param.split(",");
            String field = parts[0].trim();
            Direction direction = Direction.Ascending;

            if (parts.length > 1) {
                String dirStr = parts[1].trim().toLowerCase();
                if (dirStr.equals("desc")) {
                    direction = Direction.Descending;
                }
            }

            if (sort == null) {
                sort = Sort.by(field, direction);
            } else {
                sort = sort.and(field, direction);
            }
        }

        return sort;
    }
}
