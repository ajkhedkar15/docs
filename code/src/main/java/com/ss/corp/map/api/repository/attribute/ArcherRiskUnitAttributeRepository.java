package com.ss.corp.map.api.repository.attribute;

import com.ss.corp.map.api.entity.attribute.ArcherRiskUnitAttribute;

public interface ArcherRiskUnitAttributeRepository extends AttributeRepository<ArcherRiskUnitAttribute> {

    @Override
    default String attributeType() {
        return "archerRiskUnit";
    }
}
