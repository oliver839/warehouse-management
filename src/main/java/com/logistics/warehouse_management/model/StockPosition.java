package com.logistics.warehouse_management.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"inventory_item_id", "bin_location_id"}))
public class StockPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JsonIgnore
    private InventoryItem inventoryItem;

    @ManyToOne
    private Batch batch;

    @ManyToOne
    private Tenant tenant;

    @ManyToOne
    private UnitOfMeasure unit;

    @ManyToOne
    private BinLocation binLocation;

    private Integer quantity = 0;

    private Integer reservedQuantity = 0;

    @Enumerated(EnumType.STRING)
    private StockStatus status = StockStatus.AVAILABLE;

    @Version
    private Long version;
}