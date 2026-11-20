package com.ss.corp.map.api.enums;

/** Where a dropdown's values come from. */
public enum FilterSource {
    /** A column of the auditable_entity table (AE ID, AE Name, Business Area, Division). */
    AE_COLUMN,
    /** The ae_owner array of the auditable_entity table. */
    AE_OWNER,
    /** Tagged attributes of one attribute type (auditable_entity_attributes). */
    ATTRIBUTE
}
