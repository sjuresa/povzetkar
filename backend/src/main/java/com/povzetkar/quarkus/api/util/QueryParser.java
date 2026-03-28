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
        return parseSort(sortParams, defaultSort, allowedFields, null);
    }

    public static Sort parseSort(List<String> sortParams, Sort defaultSort, Set<String> allowedFields, String fieldPrefix) {
        if (sortParams == null || sortParams.isEmpty()) {
            if (defaultSort != null && fieldPrefix != null) {
                // If we have a default sort but also a prefix, we should ideally qualify the default sort too
                // But for simplicity in this project, we assume defaultSort passed is already correct or handles it.
                // To be robust, we could rebuild defaultSort with prefix.
            }
            return defaultSort;
        }

        Sort sort = null;

        for (String param : sortParams) {
            String[] parts = param.split(",");
            String field = parts[0].trim();
            
            if (!allowedFields.contains(field)) {
                throw new BadRequestException("Invalid sort field: " + field + ". Allowed fields are: " + allowedFields);
            }

            String qualifiedField = fieldPrefix != null ? fieldPrefix + "." + field : field;
            Direction direction = Direction.Ascending;

            if (parts.length > 1) {
                String dirStr = parts[1].trim().toLowerCase();
                if (dirStr.equals("desc")) {
                    direction = Direction.Descending;
                }
            }

            if (sort == null) {
                sort = Sort.by(qualifiedField, direction);
            } else {
                sort = sort.and(qualifiedField, direction);
            }
        }

        return sort;
    }
}
