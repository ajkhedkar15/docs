package com.ss.corp.map.api.entity.attribute;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

/**
 * Columns every attribute type table shares. Read-only: each subclass is {@code @Immutable}.
 * TODO: match the column names to your tables.
 */
@Getter
@MappedSuperclass
public abstract class AttributeEntity {

    @Id
    @Column(name = "ATTRIBUTE_ID")
    private String attributeId;

    @Column(name = "NAME")
    private String name;

    @Column(name = "ACTIVE")
    private boolean active;
}
