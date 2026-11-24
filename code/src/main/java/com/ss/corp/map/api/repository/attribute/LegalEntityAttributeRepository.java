package com.ss.corp.map.api.repository.attribute;

import com.ss.corp.map.api.entity.attribute.LegalEntityAttribute;

public interface LegalEntityAttributeRepository extends AttributeRepository<LegalEntityAttribute> {

    @Override
    default String attributeType() {
        return "legalEntity";
    }
}
