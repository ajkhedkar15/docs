package com.ss.corp.map.api.repository;

import com.ss.corp.map.api.entity.AuditableEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface AuditableEntityRepository
        extends JpaRepository<AuditableEntity, UUID>, JpaSpecificationExecutor<AuditableEntity> {

    List<AuditableEntity> findByAeIdIn(Collection<String> aeIds);

    Optional<AuditableEntity> findByAeIdAndAeStatusTrue(String aeId);

    // ---- Dropdown values (API 1): active AEs only, sorted, no duplicates ----

    @Query("select distinct a.aeId from AuditableEntity a where a.aeStatus = true order by a.aeId")
    List<String> findDistinctAeIds();

    @Query("select distinct a.aeName from AuditableEntity a where a.aeStatus = true order by a.aeName")
    List<String> findDistinctAeNames();

    @Query("""
            select distinct a.businessArea from AuditableEntity a
            where a.aeStatus = true and a.businessArea is not null
            order by a.businessArea""")
    List<String> findDistinctBusinessAreas();

    @Query("""
            select distinct a.division from AuditableEntity a
            where a.aeStatus = true and a.division is not null
            order by a.division""")
    List<String> findDistinctDivisions();

    /** One row per owner name: unpacking the text[] array needs PostgreSQL's unnest, so this is native SQL. */
    @Query(value = """
            SELECT DISTINCT owner
            FROM aur_map_app.auditable_entity, unnest(ae_owner) AS owner
            WHERE ae_status = true
            ORDER BY 1""", nativeQuery = true)
    List<String> findDistinctOwners();
}
