package com.talon.core.stores.internal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stores")
@Getter
public class Store {

    @Id
    @GeneratedValue
    private UUID id;

    @Setter
    @Column(nullable = false)
    private String name;

    @Setter
    private String address;

    @Setter
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "USD";

    @Setter
    @Column(name = "tax_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal taxRate = new BigDecimal("0.15");

    @Setter
    @Column(nullable = false)
    private String timezone = "Africa/Harare";

    @Setter
    @Column(name = "receipt_header")
    private String receiptHeader;

    @Setter
    @Column(name = "receipt_footer")
    private String receiptFooter;

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
