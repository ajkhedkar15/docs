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
 * AE = a column of the AE table, ATTRIBUTE_TYPE = one of the 11 attribute types.
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
    RISK("risk", ATTRIBUTE_TYPE, ADVANCED),
    LEGAL_ENTITY("legalEntity", ATTRIBUTE_TYPE, ADVANCED),
    REGULATION("regulation", ATTRIBUTE_TYPE, ADVANCED),
    THIRD_PARTY("thirdParty", ATTRIBUTE_TYPE, ADVANCED),
    APPLICATION("application", ATTRIBUTE_TYPE, ADVANCED),
    BUSINESS_AREA("businessArea", AE, ADVANCED),
    MODEL("model", ATTRIBUTE_TYPE, ADVANCED),
    ARCHER_RISK_UNIT("archerRiskUnit", ATTRIBUTE_TYPE, ADVANCED),
    COST_CENTER("costCenter", ATTRIBUTE_TYPE, ADVANCED),
    LOCATION("location", ATTRIBUTE_TYPE, ADVANCED),
    PRODUCT("product", ATTRIBUTE_TYPE, ADVANCED);

    private final String key;
    private final Source source;
    private final FilterGroup group;
}
