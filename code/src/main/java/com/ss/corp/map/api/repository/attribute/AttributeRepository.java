package com.ss.corp.map.api.repository.attribute;

import com.ss.corp.map.api.entity.attribute.AttributeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;

/**
 * Shared by the 11 attribute type repositories. Each one only says which attribute type it serves.
 */
@NoRepositoryBean
public interface AttributeRepository<T extends AttributeEntity> extends JpaRepository<T, String> {

    /** The filter key this repository serves, e.g. {@code legalEntity}. */
    String attributeType();

    /** Every active name, sorted, no duplicates. */
    @Query("select distinct e.name from #{#entityName} e where e.active = true order by e.name")
    List<String> findDistinctActiveNames();
}
