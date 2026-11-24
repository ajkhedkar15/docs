package com.ss.corp.map.api.service.impl;

import com.ss.corp.map.api.model.FilterOption;
import com.ss.corp.map.api.model.FilterResponse;
import com.ss.corp.map.api.enums.FilterGroup;
import com.ss.corp.map.api.enums.Screen;
import com.ss.corp.map.api.enums.ScreenOption;
import com.ss.corp.map.api.repository.AttributeOptionRepository;
import com.ss.corp.map.api.service.AEService;
import com.ss.corp.map.api.service.FilterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FilterServiceImpl implements FilterService {

    private final AEService aeService;

    /** attribute type key (e.g. est) -> the repository of its master table */
    private final Map<String, AttributeOptionRepository<?, ?>> attributeRepositories;

    /** Spring injects every AttributeOptionRepository; adding a type needs no change here. */
    public FilterServiceImpl(AEService aeService, List<AttributeOptionRepository<?, ?>> attributeRepositories) {
        this.aeService = aeService;
        this.attributeRepositories = attributeRepositories.stream()
                .collect(Collectors.toMap(AttributeOptionRepository::type, Function.identity()));
    }

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

    /** The attribute type dropdowns (est, ...): one query on that type's master table. */
    private List<String> attributeOptions(ScreenOption option) {
        AttributeOptionRepository<?, ?> repository = attributeRepositories.get(option.key());
        if (repository == null) {
            throw new IllegalStateException("No repository for attribute type " + option.key());
        }
        return repository.findOptions();
    }
}
