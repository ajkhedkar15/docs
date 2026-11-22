package com.ss.corp.map.api.model;

import com.ss.corp.map.api.entity.AuditableEntity;
import java.time.Instant;

/** One AE card in the search results. */
public record AEResponse(
        String aeId,
        String aeName,
        String aeDescription,
        String businessArea,
        Instant modifiedOn,
        int recommendationCount,
        boolean isOwner,
        Person lockedBy) {

    /** Entity → DTO. */
    public static AEResponse from(AuditableEntity ae, String currentUser) {
        return new AEResponse(
                ae.getAeId(),
                ae.getAeName(),
                ae.getAeDesc(),
                ae.getBusinessArea(),
                ae.getModifiedOn(),
                0,                          // recommendationCount: once recommendations exist
                ae.isOwnedBy(currentUser),
                null);                      // lockedBy: once the lock API exists
    }
}
