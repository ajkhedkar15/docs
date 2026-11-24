package com.ss.corp.map.api.repository.attribute;

import com.ss.corp.map.api.entity.attribute.ModelAttribute;

public interface ModelAttributeRepository extends AttributeRepository<ModelAttribute> {

    @Override
    default String attributeType() {
        return "model";
    }
}
