package com.talon.core.domain;

import java.util.Arrays;

/**
 * Mirrors the realm roles defined in the Keycloak "talon" realm exactly
 * (lowercase, snake_case) so no translation table is needed between a
 * Keycloak role claim and this enum.
 */
public enum Role {
    SUPER_ADMIN("super_admin"),
    ADMIN("admin"),
    MANAGER("manager"),
    CASHIER("cashier");

    private final String value;

    Role(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static Role fromValue(String value) {
        return Arrays.stream(values())
            .filter(role -> role.value.equals(value))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown role: " + value));
    }
}
