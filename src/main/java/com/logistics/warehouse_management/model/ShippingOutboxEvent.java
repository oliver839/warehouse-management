package com.logistics.warehouse_management.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class ShippingOutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    private Long deliveryNoteId;

    private String payload;

    @Enumerated(EnumType.STRING)
    private ShippingOutboxStatus status = ShippingOutboxStatus.PENDING;

    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime nextAttemptAt;

    private LocalDateTime processedAt;

    private Integer attempts = 0;

    private LocalDateTime processingStartedAt;

    private String workerId;

    @Version
    private Long version;
}