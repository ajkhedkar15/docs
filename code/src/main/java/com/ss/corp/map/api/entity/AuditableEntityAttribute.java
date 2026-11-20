package com.ss.corp.map.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One attribute tagged to an AE.
 *
 * <p>One row per AE + attribute type + attribute (uq_ae_attr). No unique constraint on ae_id, attr_id or
 * attr_name alone: an AE has many attributes, and an attribute can be tagged to many AEs.
 * No @ManyToOne to AuditableEntity: services only need aeId, and a join on a non-primary-key column
 * would make Hibernate load the AE for every row.
 */
@Entity
@Table(
        schema = "aur_map_app",
        name = "auditable_entity_attributes",
        uniqueConstraints = @UniqueConstraint(name = "uq_ae_attr", columnNames = {"ae_id", "attr_type", "attr_id"}),
        indexes = {
            @Index(name = "ix_ae_attr_ae_id", columnList = "ae_id"),
            @Index(name = "ix_ae_attr_type_name", columnList = "attr_type, attr_name")
        })
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditableEntityAttribute {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "ae_id", nullable = false)
    private String aeId;

    /** One of the 11 attribute types, e.g. "est", "legalEntity". */
    @Column(name = "attr_type", nullable = false, length = 30)
    private String attrType;

    @Column(name = "attr_id", nullable = false)
    private String attrId;

    @Column(name = "attr_name", nullable = false, length = 500)
    private String attrName;

    /** Why it was tagged. Required for new tags (checked in the service). */
    @Column(name = "attr_comment", columnDefinition = "text")
    private String attrComment;

    /** true = active; removed tags are set to false, never deleted. */
    @Column(name = "attr_status", nullable = false)
    private boolean attrStatus;

    @Column(name = "modified_on")
    private Instant modifiedOn;

    @Column(name = "modified_by")
    private String modifiedBy;
}
