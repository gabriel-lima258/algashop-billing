package com.algaworks.algashop.billing.domain.model;

import org.apache.commons.validator.routines.EmailValidator;

import java.util.Objects;

public class FieldValidations {

    private FieldValidations() {
    }

    public static void requiresNonBlank(String value) {
        requiresNonBlank(value, "");
    }

    public static void requiresNonBlank(String value, String errorMessage) {
        Objects.requireNonNull(value);
        if (value.isBlank()) {
            throw new IllegalArgumentException();
        }
    }

    public static void requiresEmailValid(String email) {
        Objects.requireNonNull(email);
    }

    public static void requiresEmailValid(String email, String errorMessage) {
        Objects.requireNonNull(email, errorMessage);
        if (email.isBlank()) {
            throw new IllegalArgumentException(errorMessage);
        }
        if (!EmailValidator.getInstance().isValid(email)) {
            throw new IllegalArgumentException(errorMessage);
        }
    }
}
