package com.ss.corp.map.api.model;

import java.util.List;
import org.springframework.data.domain.Page;

/** Every list response: { page, pageSize, totalRecords, totalPages, items }. Pages start at 1. */
public record PagedResponse<T>(int page, int pageSize, long totalRecords, int totalPages, List<T> items) {

    public PagedResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }

    /** From a Spring Data page (0-based) to the API's 1-based page. */
    public static <T> PagedResponse<T> of(Page<?> source, List<T> items) {
        return new PagedResponse<>(
                source.getNumber() + 1,
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages(),
                items);
    }
}
