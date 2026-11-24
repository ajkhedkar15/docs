package com.ss.corp.map.api.repository.attribute;

import com.ss.corp.map.api.entity.attribute.RiskAttribute;

public interface RiskAttributeRepository extends AttributeRepository<RiskAttribute> {

    @Override
    default String attributeType() {
        return "risk";
    }
}
