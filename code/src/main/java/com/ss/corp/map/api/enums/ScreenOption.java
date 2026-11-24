package com.ss.corp.map.api.enums;

/**
 * One dropdown of a screen. Every per-screen option enum implements this,
 * so FilterServiceImpl can load any screen without knowing which one it is.
 */
public interface ScreenOption {

    /** Where the dropdown's values come from. */
    enum Source {
        /** A column of the AE table (aeId, aeName, aeOwner, businessDivision). */
        AE,
        /** One of the attribute types (est, risk, legalEntity, ...). */
        ATTRIBUTE_TYPE
    }

    /** The filter type as used in the API, e.g. {@code aeOwner} or {@code legalEntity}. */
    String key();

    Source source();

    /** BASIC = shown when the screen loads, ADVANCED = under "Advanced filters". Differs per screen. */
    FilterGroup group();
}
