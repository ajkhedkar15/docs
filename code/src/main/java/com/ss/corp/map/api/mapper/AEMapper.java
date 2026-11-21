package com.ss.corp.map.api.mapper;

import com.ss.corp.map.api.entity.AuditableEntity;
import com.ss.corp.map.api.model.AEResponse;
import org.springframework.stereotype.Component;

/** AuditableEntity → AEResponse (one card). */
@Component
public class AEMapper {

    public AEResponse toResponse(AuditableEntity ae, String currentUser) {
        return new AEResponse(
                ae.getAeId(),
                ae.getAeName(),
                ae.getAeDesc(),
                ae.getBusinessArea(),
                ae.getModifiedOn(),
                0,                          // recommendationCount: set once recommendations exist
                ae.isOwnedBy(currentUser),
                null);                      // lockedBy: set once the lock API exists
    }
}
