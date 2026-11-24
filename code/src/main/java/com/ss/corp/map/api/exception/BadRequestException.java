package com.ss.corp.map.api.exception;

import java.util.List;

/** 400 Bad Request, optionally with the fields that are wrong. Same as the one in aur_map_api_svc. */
public class BadRequestException extends RuntimeException {

    private final List<FieldError> errors;

    public BadRequestException(String message) {
        this(message, List.of());
    }

    public BadRequestException(String message, List<FieldError> errors) {
        super(message);
        this.errors = List.copyOf(errors);
    }

    public List<FieldError> getErrors() {
        return errors;
    }
}
