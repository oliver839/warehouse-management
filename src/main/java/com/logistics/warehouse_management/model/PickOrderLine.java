package com.logistics.warehouse_management.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
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

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class PickOrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonBackReference
    @ManyToOne(optional = false)
    private PickOrder pickOrder;

    @ManyToOne(optional = false)
    private InventoryItem inventoryItem;

    private Integer requiredQuantity;

    private Integer pickedQuantity = 0;

    @ManyToOne(optional = false)
    private BinLocation binLocation;

    @Enumerated(EnumType.STRING)
    private PickLineStatus status = PickLineStatus.OPEN;
}