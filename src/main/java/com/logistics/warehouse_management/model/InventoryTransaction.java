package com.logistics.warehouse_management.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class InventoryTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime timestamp;

    @ManyToOne(optional = false)
    private InventoryItem inventoryItem;

    private Integer quantityDelta;

    private Integer previousQuantity;

    private Integer newQuantity;

    @Enumerated(EnumType.STRING)
    private InventoryTransactionReason reason;

    private String referenceType;

    private Long referenceId;

    private String username;
}