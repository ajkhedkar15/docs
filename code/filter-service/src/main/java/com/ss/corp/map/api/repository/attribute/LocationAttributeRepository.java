package com.ss.corp.map.api.repository.attribute;

import com.ss.corp.map.api.entity.attribute.LocationAttribute;

public interface LocationAttributeRepository extends AttributeRepository<LocationAttribute> {

    @Override
    default String attributeType() {
        return "location";
    }
}
