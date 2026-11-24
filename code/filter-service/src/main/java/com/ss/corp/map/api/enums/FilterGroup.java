package com.ss.corp.map.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import com.ss.corp.map.api.exception.BadRequestException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/** Where a dropdown sits on the screen: shown on load (BASIC) or under "Advanced filters" (ADVANCED). */
@Getter
@RequiredArgsConstructor
public enum FilterGroup {

    BASIC("basic"),
    ADVANCED("advanced");

    @JsonValue
    private final String key;

    /** null or blank = no group (all dropdowns). @throws BadRequestException (400) for an unknown group */
    public static FilterGroup fromKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(group -> group.key.equalsIgnoreCase(key))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Unknown filter group: " + key));
    }
}
