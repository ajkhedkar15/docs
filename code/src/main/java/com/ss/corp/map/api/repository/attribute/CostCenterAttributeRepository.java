package com.ss.corp.map.api.repository.attribute;

import com.ss.corp.map.api.entity.attribute.CostCenterAttribute;

public interface CostCenterAttributeRepository extends AttributeRepository<CostCenterAttribute> {

    @Override
    default String attributeType() {
        return "costCenter";
    }
}
