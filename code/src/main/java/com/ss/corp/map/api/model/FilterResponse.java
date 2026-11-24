package com.ss.corp.map.api.model;

import java.util.List;

/** A screen's dropdowns, in screen order. */
public record FilterResponse(String screen, List<FilterOption> filters) {
}
