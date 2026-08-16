package com.talon.core.users.internal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Flat user↔store join (FKs, not a bidirectional JPA association). */
@Entity
@Table(name = "user_stores", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "store_id"}))
@Getter
public class UserStore {

    @Id
    @GeneratedValue
    private UUID id;

    @Setter
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Setter
    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Setter
    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @jakarta.persistence.PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
