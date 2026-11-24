package com.ss.corp.map.api.repository.attribute;

import com.ss.corp.map.api.entity.attribute.ApplicationAttribute;

public interface ApplicationAttributeRepository extends AttributeRepository<ApplicationAttribute> {

    @Override
    default String attributeType() {
        return "application";
    }
}
