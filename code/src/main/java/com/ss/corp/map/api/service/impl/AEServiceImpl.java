package com.ss.corp.map.api.service.impl;

import com.ss.corp.map.api.entity.AuditableEntity;
import com.ss.corp.map.api.entity.AuditableEntityAttribute;
import com.ss.corp.map.api.enums.Screen;
import com.ss.corp.map.api.enums.ScreenFilter;
import com.ss.corp.map.api.exception.ValidationException;
import com.ss.corp.map.api.mapper.AEMapper;
import com.ss.corp.map.api.model.AEResponse;
import com.ss.corp.map.api.model.FilterRequest;
import com.ss.corp.map.api.model.PagedResponse;
import com.ss.corp.map.api.repository.AuditableEntityRepository;
import com.ss.corp.map.api.service.AEService;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AEServiceImpl implements AEService {

    private static final int MAX_PAGE_SIZE = 100;

    /** aeId as a tiebreaker keeps paging stable when names repeat. */
    private static final Sort SORT = Sort.by("aeName", "aeId");

    private final AuditableEntityRepository aeRepository;
    private final AEMapper aeMapper;

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<AEResponse> search(FilterRequest request, int page, int pageSize, String currentUser) {
        validatePaging(page, pageSize);
        Screen screen = Screen.fromValue(request.screen());

        Page<AuditableEntity> result = aeRepository.findAll(
                buildSpec(screen, request.filters()),
                PageRequest.of(page - 1, pageSize, SORT));               // API 1-based → Spring 0-based

        List<AEResponse> items = result.getContent().stream()
                .map(ae -> aeMapper.toResponse(ae, currentUser))
                .toList();

        return PagedResponse.of(result, items);
    }

    /** OR within a dropdown, AND across dropdowns. Empty filters = all active AEs. */
    private Specification<AuditableEntity> buildSpec(Screen screen, Map<String, List<String>> selected) {
        Set<String> allowedKeys = screen.getFilters().stream()
                .map(ScreenFilter::getKey)
                .collect(Collectors.toSet());

        return (root, query, cb) -> {
            List<Predicate> and = new ArrayList<>();
            and.add(cb.isTrue(root.get("aeStatus")));

            if (selected != null) {
                selected.forEach((key, values) -> {
                    if (!allowedKeys.contains(key)) {
                        throw new ValidationException("filters." + key, "Unknown filter for this screen");
                    }
                    if (values != null && !values.isEmpty()) {
                        and.add(condition(key, values, root, query, cb));
                    }
                });
            }
            return cb.and(and.toArray(Predicate[]::new));
        };
    }

    /** AE columns match directly; every other key is an attribute type. */
    private Predicate condition(String key, List<String> values,
                                Root<AuditableEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        return switch (key) {
            case "aeId" -> root.get("aeId").in(values);
            case "aeName" -> root.get("aeName").in(values);
            case "businessArea" -> root.get("businessArea").in(values);
            case "businessDivision" -> root.get("division").in(values);
            // ae_owner && values (PostgreSQL array overlap). Needs Hibernate 6.4+ (Spring Boot 3.2+).
            case "aeOwner" -> ((HibernateCriteriaBuilder) cb).collectionOverlaps(root.get("aeOwner"), values);
            default -> cb.exists(taggedWith(key, values, root, query, cb));
        };
    }

    /** EXISTS an active tag of this type with one of these names on the AE. */
    private Subquery<Integer> taggedWith(String type, List<String> names,
                                         Root<AuditableEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        Subquery<Integer> sq = query.subquery(Integer.class);
        Root<AuditableEntityAttribute> tag = sq.from(AuditableEntityAttribute.class);
        return sq.select(cb.literal(1)).where(
                cb.equal(tag.get("aeId"), root.get("aeId")),
                cb.equal(tag.get("attrType"), type),
                tag.get("attrName").in(names),
                cb.isTrue(tag.get("attrStatus")));
    }

    private void validatePaging(int page, int pageSize) {
        if (page < 1) {
            throw new ValidationException("page", "Must be 1 or more");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new ValidationException("pageSize", "Must be between 1 and " + MAX_PAGE_SIZE);
        }
    }
}
