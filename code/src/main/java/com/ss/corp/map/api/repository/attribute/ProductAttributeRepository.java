package com.ss.corp.map.api.repository.attribute;

import com.ss.corp.map.api.entity.attribute.ProductAttribute;

public interface ProductAttributeRepository extends AttributeRepository<ProductAttribute> {

    @Override
    default String attributeType() {
        return "product";
    }
}
