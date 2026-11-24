package com.ss.corp.map.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * One EST (master list, read-only here). Feeds the EST dropdown.
 * Each attribute type has its own master table with its own columns, so each gets its own entity.
 * TODO: check the schema and the column types against the table.
 */
@Entity
@Immutable
@Table(schema = "aur_map_app", name = "est_master")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EstMaster {

    @Id
    @Column(name = "est_id")
    private String estId;

    @Column(name = "est_name")
    private String estName;

    @Column(name = "est_desc")
    private String estDesc;

    @Column(name = "est_business_func_id")
    private String estBusinessFuncId;

    @Column(name = "est_service_id")
    private String estServiceId;

    @Column(name = "last_updated_on")
    private Instant lastUpdatedOn;

    @Column(name = "transformation_id")
    private String transformationId;
}
