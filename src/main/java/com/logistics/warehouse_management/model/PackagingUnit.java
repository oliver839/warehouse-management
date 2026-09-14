package com.logistics.warehouse_management.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class PackagingUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private InventoryItem inventoryItem;

    private String name;

    private java.math.BigDecimal quantityInBaseUnit;

    @Column(unique = true)
    private String barcode;

    private Double length;
    private Double width;
    private Double height;
    private Double weight;
}