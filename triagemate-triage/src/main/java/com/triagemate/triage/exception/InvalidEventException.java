package com.triagemate.triage.exception;

import jakarta.validation.ConstraintViolation;

import java.util.Set;
import java.util.stream.Collectors;

public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message) {
        super(message);
    }

    public InvalidEventException(Set<? extends ConstraintViolation<?>> violations) {
        super(buildMessage(violations));
    }

    private static String buildMessage(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream()
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .collect(Collectors.joining(", ", "Invalid event envelope: ", ""));
    }
}
