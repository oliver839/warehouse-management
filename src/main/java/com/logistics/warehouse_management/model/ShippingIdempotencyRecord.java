package com.logistics.warehouse_management.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.time.LocalDateTime;

/**
 * Persistent idempotency record for carrier shipments.
 * Survives JVM restarts — unlike the in-memory map in DemoShippingProvider.
 * The idempotency key is the primary key: same key always maps to the same
 * shipment, never a second one.
 */
@Entity
public class ShippingIdempotencyRecord {

    @Id
    private String idempotencyKey;

    @Column(nullable = false)
    private String shipmentId;

    private String trackingNumber;

    private LocalDateTime createdAt;

    public ShippingIdempotencyRecord() {
    }

    public ShippingIdempotencyRecord(String idempotencyKey, String shipmentId,
                                     String trackingNumber, LocalDateTime createdAt) {
        this.idempotencyKey = idempotencyKey;
        this.shipmentId = shipmentId;
        this.trackingNumber = trackingNumber;
        this.createdAt = createdAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getShipmentId() {
        return shipmentId;
    }

    public void setShipmentId(String shipmentId) {
        this.shipmentId = shipmentId;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
