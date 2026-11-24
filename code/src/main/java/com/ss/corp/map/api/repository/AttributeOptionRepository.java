package com.ss.corp.map.api.repository;

import java.util.List;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

/**
 * Parent of every attribute type's master-table repository (EST, Legal Entities, ...).
 * Each table has its own structure, so each child writes its own query for {@link #findOptions()};
 * FilterServiceImpl only sees this parent and picks the child by {@link #type()}.
 */
@NoRepositoryBean
public interface AttributeOptionRepository<T, ID> extends Repository<T, ID> {

    /** The filter key this repository serves, e.g. {@code est}. Must match the key in the screen's option enum. */
    String type();

    /** The dropdown values: every name of this attribute type, sorted, no duplicates. */
    List<String> findOptions();
}
