package com.ss.corp.map.api.entity.attribute;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "MAP_REGULATION") // TODO: real table name
public class RegulationAttribute extends AttributeEntity {
}
