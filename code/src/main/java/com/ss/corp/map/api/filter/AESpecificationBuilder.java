package com.ss.corp.map.api.filter;

import com.ss.corp.map.api.entity.AuditableEntity;
import com.ss.corp.map.api.entity.AuditableEntityAttribute;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Turns the selected filters into a JPA Specification on AuditableEntity.
 *
 * <p>OR within a filter type (IN / overlap), AND across types. Only active AEs and active tags count.
 */
@Component
@RequiredArgsConstructor
public class AESpecificationBuilder {

    private final AttributeTaggingFilters filters;

    public Specification<AuditableEntity> build(Map<String, List<String>> selected) {
        return (root, query, cb) -> {
            List<Predicate> and = new ArrayList<>();
            and.add(cb.isTrue(root.get("aeStatus")));

            selected.forEach((type, values) -> {
                if (values == null || values.isEmpty()) {
                    return;                                             // empty dropdown = no filter
                }
                FilterDefinition definition = filters.get(type);       // unknown type → 400
                switch (definition.source()) {
                    case AE_COLUMN -> and.add(root.get(definition.field()).in(values));
                    case AE_OWNER -> and.add(ownerOverlaps(root, cb, values));
                    case ATTRIBUTE -> and.add(cb.exists(taggedWith(root, query, cb, definition.type(), values)));
                }
            });
            return cb.and(and.toArray(Predicate[]::new));
        };
    }

    /**
     * ae_owner && values (PostgreSQL array overlap). Needs Hibernate 6.4+ (Spring Boot 3.2+).
     * Cover it with a test; if your Hibernate version doesn't translate it, filter owners with native SQL instead.
     */
    private Predicate ownerOverlaps(Root<AuditableEntity> root, CriteriaBuilder cb, List<String> owners) {
        HibernateCriteriaBuilder hcb = (HibernateCriteriaBuilder) cb;
        return hcb.collectionOverlaps(root.get("aeOwner"), owners);
    }

    private Subquery<Integer> taggedWith(
            Root<AuditableEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb, String type, List<String> names) {
        Subquery<Integer> subquery = query.subquery(Integer.class);
        Root<AuditableEntityAttribute> tag = subquery.from(AuditableEntityAttribute.class);
        return subquery.select(cb.literal(1)).where(
                cb.equal(tag.get("aeId"), root.get("aeId")),
                cb.equal(tag.get("attrType"), type),
                tag.get("attrName").in(names),
                cb.isTrue(tag.get("attrStatus")));
    }
}
