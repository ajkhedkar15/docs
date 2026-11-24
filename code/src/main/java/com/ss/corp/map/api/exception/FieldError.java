package com.ss.corp.map.api.exception;

/** One invalid field in a 400 response: { field, message }. */
public record FieldError(String field, String message) {
}
