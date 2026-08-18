package com.talon.core.users.internal.entity;

import java.util.Arrays;

/**
 * Mirrors the Keycloak groups defined in the "talon" realm exactly
 * (lowercase, snake_case) so no translation table is needed between a
 * Keycloak groups claim and this enum. Each group bundles a set of
 * fine-grained realm roles in Keycloak — the API itself never checks group
 * membership, only the individual roles it grants.
 */
public enum Group {
    SUPER_ADMIN("super_admin"),
    ADMIN("admin"),
    MANAGER("manager"),
    CASHIER("cashier");

    private final String value;

    Group(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static Group fromValue(String value) {
        return Arrays.stream(values())
            .filter(group -> group.value.equals(value))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown group: " + value));
    }
}
