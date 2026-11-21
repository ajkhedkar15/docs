package com.ss.corp.map.api.enums;

/** A dropdown on a screen. Each screen's filter enum implements this, so Screen can hold any of them. */
public interface ScreenFilter {

    /** API key of the dropdown, e.g. "legalEntity". */
    String getKey();
}
