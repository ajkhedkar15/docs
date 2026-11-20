package com.ss.corp.map.api.model;

import java.util.List;

/** Response of GET /{screen}/filters: the screen and all its dropdowns, in screen order. */
public record FilterResponse(String screen, List<FilterOption> filters) {

    public FilterResponse {
        filters = filters == null ? List.of() : List.copyOf(filters);
    }
}
