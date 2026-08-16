package com.talon.core.users.internal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * The app's own user profile — everything Keycloak doesn't own. Credentials,
 * email verification, and password state live in Keycloak; keycloakId is the
 * join key back to that identity. PIN and store assignment stay local since
 * they're app-level concerns Keycloak has no concept of.
 */
@Entity
@Table(name = "users")
@Getter
public class User {

    @Id
    @GeneratedValue
    private UUID id;

    @Setter
    @Column(name = "keycloak_id", nullable = false, unique = true)
    private UUID keycloakId;

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

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
