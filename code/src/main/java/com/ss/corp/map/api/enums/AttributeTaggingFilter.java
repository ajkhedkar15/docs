package com.ss.corp.map.api.enums;

import lombok.Getter;

/** Attribute Tagging dropdowns, in screen order. */
@Getter
public enum AttributeTaggingFilter implements ScreenFilter {

    AE_ID("aeId"),
    AE_NAME("aeName"),
    AE_OWNER("aeOwner"),
    EST("est"),
    RISK("risk"),
    LEGAL_ENTITY("legalEntity"),
    REGULATION("regulation"),
    THIRD_PARTY("thirdParty"),
    APPLICATION("application"),
    BUSINESS_AREA("businessArea"),
    BUSINESS_DIVISION("businessDivision"),
    MODEL("model"),
    ARCHER_RISK_UNIT("archerRiskUnit"),
    COST_CENTER("costCenter"),
    LOCATION("location"),
    PRODUCT("product");

    private final String key;

    AttributeTaggingFilter(String key) {
        this.key = key;
    }
}
