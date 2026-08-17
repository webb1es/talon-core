package com.talon.core.users.internal.entity;

import com.talon.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * The app's own user profile — everything Keycloak doesn't own. Credentials,
 * email verification, and password state live in Keycloak; id is the
 * Keycloak subject itself, so it doubles as the join key back to that
 * identity. PIN and store assignment stay local since they're app-level
 * concerns Keycloak has no concept of.
 */
@Entity
@Table(name = "users")
@Getter
public class User extends BaseEntity {

    @Setter
    @Column(nullable = false, unique = true)
    private String username;

    @Setter
    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Setter
    @Column(unique = true)
    private String email;

    @Setter
    @Column(nullable = false)
    private Role role = Role.CASHIER;

    @Setter
    @Column(name = "avatar_url")
    private String avatarUrl;

    @Setter
    @Column(unique = true)
    private String phone;

    /** Hashed — cashier quick-login, unrelated to the Keycloak password. */
    @Setter
    private String pin;

    @Setter
    @Column(nullable = false)
    private boolean active = true;
}
