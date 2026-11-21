package com.ss.corp.map.api.service;

import com.ss.corp.map.api.model.FilterResponse;

/** Dropdown data for a screen's filter bar. One service for every screen. */
public interface FilterService {

    /**
     * Returns every dropdown of the screen, in the screen enum's order, each with its values.
     *
     * @param screen screen name sent by the UI, e.g. "attributeTagging"
     * @return the screen and its dropdowns
     * @throws com.ss.corp.map.api.exception.ValidationException if the screen is unknown (400)
     */
    FilterResponse getFilters(String screen);
}
