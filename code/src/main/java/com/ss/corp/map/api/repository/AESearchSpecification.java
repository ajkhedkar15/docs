package com.ss.corp.map.api.repository;

import com.ss.corp.map.api.entity.AuditableEntity;
import com.ss.corp.map.api.entity.AuditableEntityAttribute;
import com.ss.corp.map.api.enums.Screen;
import com.ss.corp.map.api.enums.ScreenOption;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.data.jpa.domain.Specification;

/**
 * The AE search as a JPA Specification: active AEs that match every selected filter.
 * OR within a filter (any of its values), AND across filters. Empty filters = all active AEs.
 *
 * <p>Like the filter options, each selected key is routed by its source:
 * AE = a column of auditable_entity, ATTRIBUTE_TYPE = the AE has one of the values tagged.
 * Call {@link Screen#requireKnownFilters} first, so every key here belongs to the screen.
 */
public final class AESearchSpecification {

    private AESearchSpecification() {
    }

    public static Specification<AuditableEntity> of(Screen screen, Map<String, List<String>> filters) {
        return (root, query, cb) -> {
            List<Predicate> and = new ArrayList<>();
            and.add(cb.isTrue(root.get("aeStatus")));

            filters.forEach((key, values) -> {
                if (values == null || values.isEmpty()) {
                    return;                                         // nothing picked = no filter
                }
                ScreenOption option = screen.findOption(key).orElseThrow();
                and.add(switch (option.source()) {
                    case AE -> aeColumn(root, cb, key, values);
                    case ATTRIBUTE_TYPE -> cb.exists(taggedWith(root, query, cb, key, values));
                });
            });
            return cb.and(and.toArray(Predicate[]::new));
        };
    }

    /** The AE filters: one column of auditable_entity is one of the values. */
    private static Predicate aeColumn(Root<AuditableEntity> root, CriteriaBuilder cb, String key, List<String> values) {
        return switch (key) {
            case "aeId" -> root.get("aeId").in(values);
            case "aeName" -> root.get("aeName").in(values);
            case "aeOwner" -> ownerIsOneOf(root, cb, values);
            case "businessArea" -> root.get("businessArea").in(values);
            case "businessDivision" -> root.get("division").in(values);
            default -> throw new IllegalStateException("No AE column for filter " + key);
        };
    }

    /**
     * ae_owner is a PostgreSQL text[]: true if any of the picked owners is in it.
     * Hibernate's array_position returns the 1-based position, or 0 when the owner isn't there. Unlike
     * {@code &&} or {@code @>}, it works with the varchar parameter Hibernate binds for a String.
     * {@code hcb.value} binds the owner as a parameter (cb.literal would inline it into the SQL).
     */
    private static Predicate ownerIsOneOf(Root<AuditableEntity> root, CriteriaBuilder cb, List<String> owners) {
        HibernateCriteriaBuilder hcb = (HibernateCriteriaBuilder) cb;
        return cb.or(owners.stream()
                .map(owner -> cb.greaterThan(cb.function("array_position", Integer.class, root.get("aeOwner"), hcb.value(owner)), 0))
                .toArray(Predicate[]::new));
    }

    /** The attribute filters: the AE has an active tag of this type whose name is one of the values. */
    private static Subquery<Integer> taggedWith(Root<AuditableEntity> root, CriteriaQuery<?> query,
                                                CriteriaBuilder cb, String attributeType, List<String> names) {
        Subquery<Integer> subquery = query.subquery(Integer.class);
        Root<AuditableEntityAttribute> tag = subquery.from(AuditableEntityAttribute.class);
        return subquery.select(cb.literal(1)).where(
                cb.equal(tag.get("aeId"), root.get("aeId")),
                cb.equal(tag.get("attrType"), attributeType),
                tag.get("attrName").in(names),
                cb.isTrue(tag.get("attrStatus")));
    }
}
