package com.ss.corp.map.api.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import static com.ss.corp.map.api.enums.FilterGroup.ADVANCED;
import static com.ss.corp.map.api.enums.FilterGroup.BASIC;
import static com.ss.corp.map.api.enums.ScreenOption.Source.AE;
import static com.ss.corp.map.api.enums.ScreenOption.Source.ATTRIBUTE_TYPE;

/**
 * Attribute Tagging's dropdowns, in the order they appear on screen.
 * AE = a column of the AE table, ATTRIBUTE_TYPE = an attribute type with its own master table.
 * Only the types whose master data exists are listed (EST for now); add the rest as their tables are mapped.
 * BASIC = shown on load, ADVANCED = under "Advanced filters".
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum AttributeTaggingOption implements ScreenOption {

    AE_ID("aeId", AE, BASIC),
    AE_NAME("aeName", AE, BASIC),
    AE_OWNER("aeOwner", AE, BASIC),
    BUSINESS_DIVISION("businessDivision", AE, BASIC),
    EST("est", ATTRIBUTE_TYPE, ADVANCED),
    BUSINESS_AREA("businessArea", AE, ADVANCED);

    private final String key;
    private final Source source;
    private final FilterGroup group;
}
