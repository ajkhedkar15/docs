package com.ss.corp.map.api.model;

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
}
