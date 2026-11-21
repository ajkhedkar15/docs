package com.ss.corp.map.api.service.impl;

import com.ss.corp.map.api.enums.Screen;
import com.ss.corp.map.api.model.FilterOption;
import com.ss.corp.map.api.model.FilterResponse;
import com.ss.corp.map.api.repository.AuditableEntityAttributeRepository;
import com.ss.corp.map.api.repository.AuditableEntityRepository;
import com.ss.corp.map.api.service.FilterService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FilterServiceImpl implements FilterService {

    private final AuditableEntityRepository aeRepository;
    private final AuditableEntityAttributeRepository attributeRepository;

    @Override
    @Transactional(readOnly = true)
    public FilterResponse getFilters(String screen) {
        log.debug("Loading filters for screen {}", screen);

        List<FilterOption> filters = Screen.fromValue(screen).getFilters().stream()
                .map(filter -> new FilterOption(filter.getKey(), findOptions(filter.getKey())))
                .toList();

        return new FilterResponse(screen, filters);
    }

    /**
     * Values for one dropdown. AE columns have their own query;
     * every other key is an attribute type in AuditableEntityAttribute.
     */
    private List<String> findOptions(String key) {
        return switch (key) {
            case "aeId" -> aeRepository.findDistinctAeIds();
            case "aeName" -> aeRepository.findDistinctAeNames();
            case "aeOwner" -> aeRepository.findDistinctOwners();
            case "businessArea" -> aeRepository.findDistinctBusinessAreas();
            case "businessDivision" -> aeRepository.findDistinctDivisions();
            default -> attributeRepository.findDistinctNamesByType(key);
        };
    }
}
