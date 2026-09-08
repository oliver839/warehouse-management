package com.logistics.warehouse_management.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(unique = true)
    private String barcode;

    private String name;

    private Integer quantityInStock;

    private Integer reservedQuantity = 0;

    private Double weightPerUnit;

    private Double spacePerUnit;

    private Double length;

    private Double width;

    private Double height;

    @ManyToOne
    private Warehouse warehouse;

    @ManyToOne
    private BinLocation binLocation;

    public int getAvailableQuantity() {
        int stock = quantityInStock == null ? 0 : quantityInStock;
        int reserved = reservedQuantity == null ? 0 : reservedQuantity;
        return stock - reserved;
    }
}
