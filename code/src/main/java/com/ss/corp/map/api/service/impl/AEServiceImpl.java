package com.ss.corp.map.api.service.impl;

import com.ss.corp.map.api.entity.AuditableEntity;
import com.ss.corp.map.api.enums.Screen;
import com.ss.corp.map.api.exception.BadRequestException;
import com.ss.corp.map.api.model.AEResponse;
import com.ss.corp.map.api.model.FilterRequest;
import com.ss.corp.map.api.model.PagedResponse;
import com.ss.corp.map.api.enums.ScreenOption;
import com.ss.corp.map.api.repository.AESpecifications;
import com.ss.corp.map.api.repository.AuditableEntityRepository;
import com.ss.corp.map.api.service.AEService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AEServiceImpl implements AEService {

    private static final Sort BY_NAME_THEN_ID = Sort.by("aeName", "aeId");

    private final AuditableEntityRepository aeRepository;

    @Override
    public List<String> findAllAeIds() {
        return aeRepository.findDistinctAeIds();
    }

    @Override
    public List<String> findAllAeNames() {
        return aeRepository.findDistinctAeNames();
    }

    @Override
    public List<String> findAllAeOwners() {
        return aeRepository.findDistinctOwners();
    }

    @Override
    public List<String> findAllBusinessAreas() {
        return aeRepository.findDistinctBusinessAreas();
    }

    @Override
    public List<String> findAllBusinessDivisions() {
        return aeRepository.findDistinctDivisions();
    }

    @Override
    public PagedResponse<AEResponse> search(Screen screen, FilterRequest request, int page, int pageSize, String userName) {
        screen.requireKnownFilters(request.filters());
        log.debug("Searching AEs on screen {} with filters {}", screen.getKey(), request.filters().keySet());

        Specification<AuditableEntity> search = buildSearch(screen, request.filters());
        Page<AuditableEntity> result = aeRepository.findAll(search, PageRequest.of(page - 1, pageSize, BY_NAME_THEN_ID));
        if (page > 1 && page > result.getTotalPages()) {
            throw new BadRequestException("Page " + page + " is past the last page (" + result.getTotalPages() + ")");
        }

        List<AEResponse> items = new ArrayList<>();
        for (AuditableEntity ae : result.getContent()) {
            items.add(toResponse(ae, userName));
        }
        return PagedResponse.of(result, items);
    }

    /** Active AEs AND every selected filter. Inside one filter, any of its values matches (OR). */
    private Specification<AuditableEntity> buildSearch(Screen screen, Map<String, List<String>> filters) {
        Specification<AuditableEntity> search = AESpecifications.isActive();
        for (Map.Entry<String, List<String>> filter : filters.entrySet()) {
            String key = filter.getKey();
            List<String> values = filter.getValue();
            if (values.isEmpty()) {
                continue;                                   // nothing picked in this dropdown
            }
            ScreenOption option = screen.findOption(key).orElseThrow();
            Specification<AuditableEntity> condition = switch (option.source()) {
                case AE -> aeCondition(key, values);
                case ATTRIBUTE_TYPE -> attributeCondition(key, values);
            };
            search = search.and(condition);
        }
        return search;
    }

    /** AE dropdowns: a column of auditable_entity is one of the values. */
    private Specification<AuditableEntity> aeCondition(String key, List<String> values) {
        return switch (key) {
            case "aeId" -> AESpecifications.fieldIn("aeId", values);
            case "aeName" -> AESpecifications.fieldIn("aeName", values);
            case "aeOwner" -> AESpecifications.ownerIn(values);
            case "businessArea" -> AESpecifications.fieldIn("businessArea", values);
            case "businessDivision" -> AESpecifications.fieldIn("division", values);
            default -> throw new IllegalStateException("No AE column for filter " + key);
        };
    }

    /** Attribute dropdowns (est, ...): the AE has one of the values tagged. */
    private Specification<AuditableEntity> attributeCondition(String attributeType, List<String> values) {
        return AESpecifications.hasTag(attributeType, values);
    }

    private AEResponse toResponse(AuditableEntity ae, String userName) {
        return new AEResponse(
                ae.getAeId(),
                ae.getAeName(),
                ae.getAeDesc(),
                ae.getBusinessArea(),
                ae.getModifiedOn(),
                0,       // TODO recommendationCount: from the recommendations service once it exists
                ae.isOwnedBy(userName),
                null);   // TODO lockedBy: from the lock service once it exists
    }
}
