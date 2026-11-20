package com.ss.corp.map.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One Auditable Entity (AE). */
@Entity
@Table(schema = "aur_map_app", name = "auditable_entity")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditableEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Business id shown on screen; unique (the constraint also creates the index). */
    @Column(name = "ae_id", nullable = false, unique = true)
    private String aeId;

    /** Not unique: several AEs can share a name. */
    @Column(name = "ae_name", nullable = false, length = 500)
    private String aeName;

    @Column(name = "ae_desc", columnDefinition = "text")
    private String aeDesc;

    /** true = active. */
    @Column(name = "ae_status", nullable = false)
    private boolean aeStatus;

    @Column(name = "ae_internal_id", nullable = false, unique = true)
    private String aeInternalId;

    /** Owner names, stored as a PostgreSQL text[]. An AE can have several owners. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "ae_owner", columnDefinition = "text[]")
    private List<String> aeOwner = new ArrayList<>();

    @Column(name = "business_area", length = 300)
    private String businessArea;

    @Column(name = "division", length = 300)
    private String division;

    @Column(name = "modified_on")
    private Instant modifiedOn;

    /** If the database column is still named "modfied_by", use that name here until it is renamed. */
    @Column(name = "modified_by")
    private String modifiedBy;

    /** True if the given user is one of the AE's owners (case-insensitive). */
    public boolean isOwnedBy(String user) {
        return user != null && aeOwner != null && aeOwner.stream().anyMatch(user::equalsIgnoreCase);
    }
}
