package com.ss.corp.map.api.service;

import java.util.List;

public interface AttributeService {

    /** Every active value of one attribute type (e.g. {@code legalEntity}), sorted, no duplicates. */
    List<String> findNamesByType(String attributeType);
}
