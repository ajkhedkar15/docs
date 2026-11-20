package com.ss.corp.map.api.repository;

import com.ss.corp.map.api.entity.AuditableEntityAttribute;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AuditableEntityAttributeRepository extends JpaRepository<AuditableEntityAttribute, UUID> {

    /** Dropdown values for one attribute type (API 1). Tagged values for now; the master table later. */
    @Query("""
            select distinct a.attrName from AuditableEntityAttribute a
            where a.attrType = :type and a.attrStatus = true
            order by a.attrName""")
    List<String> findDistinctNamesByType(String type);

    /** An AE's current tags (API 3). */
    List<AuditableEntityAttribute> findByAeIdAndAttrStatusTrue(String aeId);

    /** All of an AE's tag rows, active and inactive (API 5: comparing on Update). */
    List<AuditableEntityAttribute> findByAeId(String aeId);
}
