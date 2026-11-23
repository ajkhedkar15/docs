package com.ss.corp.map.api.service.impl;

import com.ss.corp.map.api.entity.AuditableEntity;
import com.ss.corp.map.api.entity.AuditableEntityAttribute;
import com.ss.corp.map.api.enums.Screen;
import com.ss.corp.map.api.enums.ScreenFilter;
import com.ss.corp.map.api.exception.ValidationException;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AEServiceImpl implements AEService {

    /** aeId as a tiebreaker keeps paging stable when names repeat. */
    private static final Sort SORT = Sort.by("aeName", "aeId");

    private final AuditableEntityRepository aeRepository;

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<AEResponse> search(FilterRequest request, int page, int pageSize, String currentUser) {
        Screen screen = Screen.fromValue(request.screen());

        // Paging limits are checked in the controller (@Min/@Max).
        Page<AuditableEntity> result = aeRepository.findAll(
                buildSpec(screen, request.filters()),
                PageRequest.of(page - 1, pageSize, SORT));               // API 1-based → Spring 0-based

        List<AEResponse> items = result.getContent().stream()
                .map(ae -> AEResponse.from(ae, currentUser))
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
            case "aeOwner" -> ownerIn(values, root, cb);
            default -> cb.exists(taggedWith(key, values, root, query, cb));
        };
    }

    /** ae_owner contains any of the owners: OR of array_position(ae_owner, owner) IS NOT NULL. Works on any Hibernate 6. */
    private Predicate ownerIn(List<String> owners, Root<AuditableEntity> root, CriteriaBuilder cb) {
        return cb.or(owners.stream()
                .map(owner -> cb.isNotNull(
                        cb.function("array_position", Integer.class, root.get("aeOwner"), cb.literal(owner))))
                .toArray(Predicate[]::new));
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
}
