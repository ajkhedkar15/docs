package com.ss.corp.map.api.filter;

import com.ss.corp.map.api.enums.FilterSource;

/**
 * One dropdown of a screen.
 *
 * @param type   the filter type used in the API ("aeId", "aeOwner", "est", ...)
 * @param source where its values come from
 * @param field  the AuditableEntity field for AE_COLUMN filters (aeId, aeName, businessArea, division); null otherwise
 */
public record FilterDefinition(String type, FilterSource source, String field) {

    public static FilterDefinition aeColumn(String type, String field) {
        return new FilterDefinition(type, FilterSource.AE_COLUMN, field);
    }

    public static FilterDefinition aeOwner() {
        return new FilterDefinition("aeOwner", FilterSource.AE_OWNER, null);
    }

    public static FilterDefinition attribute(String attributeType) {
        return new FilterDefinition(attributeType, FilterSource.ATTRIBUTE, null);
    }
}
