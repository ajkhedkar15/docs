package com.ss.corp.map.api.entity.attribute;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "MAP_PRODUCT") // TODO: real table name
public class ProductAttribute extends AttributeEntity {
}
