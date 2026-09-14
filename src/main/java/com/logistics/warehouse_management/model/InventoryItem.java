package com.logistics.warehouse_management.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.FetchType;
import jakarta.persistence.Version;
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

    @Version
    private Long version;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(unique = true)
    private String barcode;

    private String name;

    private Double weightPerUnit;

    private Double spacePerUnit;

    private Double length;

    private Double width;

    private Double height;

    @ManyToOne
    private Warehouse warehouse;

    @OneToMany(mappedBy = "inventoryItem", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private java.util.List<StockPosition> stockPositions = new java.util.ArrayList<>();

}
