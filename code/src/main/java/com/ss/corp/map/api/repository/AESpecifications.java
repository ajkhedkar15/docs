package com.ss.corp.map.api.repository;

import com.ss.corp.map.api.entity.AuditableEntity;
import com.ss.corp.map.api.entity.AuditableEntityAttribute;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.data.jpa.domain.Specification;

/** The building blocks of the AE search. Each method is one condition on auditable_entity. */
public final class AESpecifications {

    private AESpecifications() {
    }

    /** Only active AEs. */
    public static Specification<AuditableEntity> isActive() {
        return (root, query, cb) -> cb.isTrue(root.get("aeStatus"));
    }

    /** The AE's field is one of the values, e.g. fieldIn("division", ["Div 1", "Div 2"]). */
    public static Specification<AuditableEntity> fieldIn(String field, List<String> values) {
        return (root, query, cb) -> root.get(field).in(values);
    }

    /**
     * At least one of the owners is in the AE's owner list (ae_owner is a PostgreSQL text[]).
     * Uses array_position: Hibernate returns the position, or 0 when the owner isn't there.
     * ({@code &&} doesn't work here: Hibernate sends the values as varchar[], not text[].)
     */
    public static Specification<AuditableEntity> ownerIn(List<String> owners) {
        return (root, query, cb) -> {
            HibernateCriteriaBuilder hcb = (HibernateCriteriaBuilder) cb;
            List<Predicate> anyOwner = new ArrayList<>();
            for (String owner : owners) {
                anyOwner.add(cb.greaterThan(
                        cb.function("array_position", Integer.class, root.get("aeOwner"), hcb.value(owner)), 0));
            }
            return cb.or(anyOwner.toArray(new Predicate[0]));
        };
    }

    /** The AE has an active tag of this attribute type whose name is one of the values. */
    public static Specification<AuditableEntity> hasTag(String attributeType, List<String> names) {
        return (root, query, cb) -> {
            Subquery<Integer> tagged = query.subquery(Integer.class);
            var tag = tagged.from(AuditableEntityAttribute.class);
            tagged.select(cb.literal(1)).where(
                    cb.equal(tag.get("aeId"), root.get("aeId")),
                    cb.equal(tag.get("attrType"), attributeType),
                    tag.get("attrName").in(names),
                    cb.isTrue(tag.get("attrStatus")));
            return cb.exists(tagged);
        };
    }
}
