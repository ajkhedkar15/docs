package com.ss.corp.map.api.repository.attribute;

import com.ss.corp.map.api.entity.attribute.RegulationAttribute;

public interface RegulationAttributeRepository extends AttributeRepository<RegulationAttribute> {

    @Override
    default String attributeType() {
        return "regulation";
    }
}
