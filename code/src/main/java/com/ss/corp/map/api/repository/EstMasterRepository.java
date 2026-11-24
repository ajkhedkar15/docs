package com.ss.corp.map.api.repository;

import com.ss.corp.map.api.entity.EstMaster;
import java.util.List;
import org.springframework.data.jpa.repository.Query;

public interface EstMasterRepository extends AttributeOptionRepository<EstMaster, String> {

    @Override
    default String type() {
        return "est";
    }

    /** EST dropdown values: every EST name, sorted, no duplicates. */
    @Override
    @Query("select distinct e.estName from EstMaster e where e.estName is not null order by e.estName")
    List<String> findOptions();
}
