package com.logistics.warehouse_management.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
public class SupplierItemReference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private InventoryItem inventoryItem;

    private String supplierName;

    @Column(nullable = false)
    private String supplierArticleNumber;

    private String supplierBarcode;

    private Integer leadTimeDays;

    private boolean preferredSupplier;
}