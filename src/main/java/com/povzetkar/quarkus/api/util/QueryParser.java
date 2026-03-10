package com.povzetkar.quarkus.api.util;

import io.quarkus.panache.common.Sort;
import io.quarkus.panache.common.Sort.Direction;
import jakarta.ws.rs.BadRequestException;

import java.util.List;
import java.util.Set;

public final class QueryParser {

    private QueryParser() {
        // Utility class
    }

    public static Sort parseSort(List<String> sortParams, Sort defaultSort, Set<String> allowedFields) {
        if (sortParams == null || sortParams.isEmpty()) {
            return defaultSort;
        }

        Sort sort = null;

        for (String param : sortParams) {
            String[] parts = param.split(",");
            String field = parts[0].trim();
            
            if (!allowedFields.contains(field)) {
                throw new BadRequestException("Invalid sort field: " + field + ". Allowed fields are: " + allowedFields);
            }

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
