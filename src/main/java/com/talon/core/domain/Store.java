package com.talon.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Bare stub, just enough to unblock the session-info store-name join. The real
 * Stores module (address, settings, etc.) is a separate, later migration.
 */
@Entity
@Table(name = "stores")
public class Store {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String name;

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
