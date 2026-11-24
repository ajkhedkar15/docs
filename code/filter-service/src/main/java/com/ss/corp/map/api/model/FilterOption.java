package com.ss.corp.map.api.model;

import com.ss.corp.map.api.enums.FilterGroup;

import java.util.List;

/** One dropdown: its type (e.g. "aeOwner", "est"), its group (basic / advanced) and every value, sorted, no duplicates. */
public record FilterOption(String type, FilterGroup group, List<String> options) {

    public FilterOption {
        options = options == null ? List.of() : List.copyOf(options);
    }
}
