package com.ss.corp.map.api.service.impl;

import com.ss.corp.map.api.model.FilterOption;
import com.ss.corp.map.api.model.FilterResponse;
import com.ss.corp.map.api.enums.FilterGroup;
import com.ss.corp.map.api.enums.Screen;
import com.ss.corp.map.api.enums.ScreenOption;
import com.ss.corp.map.api.service.AEService;
import com.ss.corp.map.api.service.AttributeService;
import com.ss.corp.map.api.service.FilterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FilterServiceImpl implements FilterService {

    private final AEService aeService;
    private final AttributeService attributeService;

    /** group = null loads every dropdown of the screen. */
    @Override
    @Transactional(readOnly = true)
    public FilterResponse getFilters(Screen screen, FilterGroup group) {
        log.debug("Loading {} filter options for screen {}", group == null ? "all" : group.getKey(), screen.getKey());
        List<FilterOption> filters = screen.getOptions().stream()
                .filter(option -> group == null || option.group() == group)
                .map(option -> new FilterOption(option.key(), option.group(), loadOptions(option)))
                .toList();
        return new FilterResponse(screen.getKey(), filters);
    }

    private List<String> loadOptions(ScreenOption option) {
        return switch (option.source()) {
            case AE -> aeOptions(option);
            case ATTRIBUTE_TYPE -> attributeOptions(option);
        };
    }

    /** The AE table dropdowns: aeId, aeName, aeOwner, businessArea, businessDivision. */
    private List<String> aeOptions(ScreenOption option) {
        return switch (option.key()) {
            case "aeId" -> aeService.findAllAeIds();
            case "aeName" -> aeService.findAllAeNames();
            case "aeOwner" -> aeService.findAllAeOwners();
            case "businessArea" -> aeService.findAllBusinessAreas();
            case "businessDivision" -> aeService.findAllBusinessDivisions();
            default -> throw new IllegalStateException("No AE options for " + option.key());
        };
    }

    /** The attribute type dropdowns: est, risk, legalEntity, ... (one query per type). */
    private List<String> attributeOptions(ScreenOption option) {
        return attributeService.findNamesByType(option.key());
    }
}
