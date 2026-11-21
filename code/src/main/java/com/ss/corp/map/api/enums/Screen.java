package com.ss.corp.map.api.enums;

import com.ss.corp.map.api.exception.ValidationException;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;

/** Screens with a filter bar. New screen = new constant + its XxxFilter enum. */
@Getter
public enum Screen {

    ATTRIBUTE_TAGGING("attributeTagging", AttributeTaggingFilter.values());
    // RISK_ASSESSMENT("riskAssessment", RiskAssessmentFilter.values());

    private final String value;
    private final List<ScreenFilter> filters;

    Screen(String value, ScreenFilter[] filters) {
        this.value = value;
        this.filters = List.of(filters);
    }

    /** The screen for the name the UI sends; unknown screen → 400. */
    public static Screen fromValue(String value) {
        return Arrays.stream(values())
                .filter(s -> s.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new ValidationException("screen", "Unknown screen: " + value));
    }
}
