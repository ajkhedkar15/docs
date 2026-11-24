package com.ss.corp.map.api.repository.attribute;

import com.ss.corp.map.api.entity.attribute.ThirdPartyAttribute;

public interface ThirdPartyAttributeRepository extends AttributeRepository<ThirdPartyAttribute> {

    @Override
    default String attributeType() {
        return "thirdParty";
    }
}
