package com.ss.corp.map.api.repository.attribute;

import com.ss.corp.map.api.entity.attribute.EstAttribute;

public interface EstAttributeRepository extends AttributeRepository<EstAttribute> {

    @Override
    default String attributeType() {
        return "est";
    }
}
