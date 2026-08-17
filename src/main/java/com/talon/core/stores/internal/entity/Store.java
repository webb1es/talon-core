package com.talon.core.stores.internal.entity;

import com.talon.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "stores")
@Getter
public class Store extends BaseEntity {

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
}
