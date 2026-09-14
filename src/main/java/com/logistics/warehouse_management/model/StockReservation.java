package com.logistics.warehouse_management.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class StockReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private StockPosition stockPosition;

    @ManyToOne
    private PickOrder pickOrder;

    private Integer quantity;

    @Enumerated(EnumType.STRING)
    private StockReservationStatus status = StockReservationStatus.ACTIVE;

    private LocalDateTime createdAt;
    private LocalDateTime releasedAt;
    private LocalDateTime fulfilledAt;
    private String username;
    private String reason;

    @Version
    private Long version;
}