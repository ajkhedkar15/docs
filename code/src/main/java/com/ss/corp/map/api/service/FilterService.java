package com.ss.corp.map.api.service;

import com.ss.corp.map.api.model.FilterResponse;
import com.ss.corp.map.api.enums.FilterGroup;
import com.ss.corp.map.api.enums.Screen;

public interface FilterService {

    /** The screen's dropdowns in screen order, only those of the group (null = all). */
    FilterResponse getFilters(Screen screen, FilterGroup group);
}
