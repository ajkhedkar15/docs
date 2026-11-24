package com.ss.corp.map.api.repository;

import com.ss.corp.map.api.entity.EstMaster;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EstMasterRepository extends JpaRepository<EstMaster, String> {

    /** EST dropdown values: every EST name, sorted, no duplicates. */
    @Query("select distinct e.estName from EstMaster e where e.estName is not null order by e.estName")
    List<String> findDistinctEstNames();
}
