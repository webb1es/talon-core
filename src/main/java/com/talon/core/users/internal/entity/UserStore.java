package com.talon.core.users.internal.entity;

import com.talon.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Flat user↔store join (FKs, not a bidirectional JPA association). */
@Entity
@Table(name = "user_stores", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "store_id"}))
@Getter
public class UserStore extends BaseEntity {

    @Setter
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Setter
    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Setter
    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;
}
