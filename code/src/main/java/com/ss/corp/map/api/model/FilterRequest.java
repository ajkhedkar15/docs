package com.ss.corp.map.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * Body of POST /{screen}/aes/search, and what a saved filter stores.
 *
 * <p>{@code filters} holds the selected values by filter type, the same text as in the dropdown options.
 * OR within a type, AND across types. An empty map means all AEs.
 */
public record FilterRequest(
        @NotBlank String screen,
        Map<@NotBlank String, @NotNull List<@NotBlank String>> filters) {

    public FilterRequest {
        filters = filters == null ? Map.of() : Map.copyOf(filters);
    }
}
