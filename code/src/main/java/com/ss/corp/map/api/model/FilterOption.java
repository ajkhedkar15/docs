package com.ss.corp.map.api.model;

import java.util.List;

/** One dropdown: its type (e.g. "aeOwner", "est") and every value as plain text, sorted, no duplicates. */
public record FilterOption(String type, List<String> options) {

    public FilterOption {
        options = options == null ? List.of() : List.copyOf(options);
    }
}
