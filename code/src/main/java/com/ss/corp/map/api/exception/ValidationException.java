package com.ss.corp.map.api.exception;

/** A request value the API can't accept. The exception handler returns 400 with the field and message. */
public class ValidationException extends RuntimeException {

    private final String field;

    public ValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
