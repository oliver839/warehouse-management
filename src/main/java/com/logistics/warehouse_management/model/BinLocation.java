package com.logistics.warehouse_management.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class BinLocation {

    public BinLocation(Long id, String code, String description, Double capacity,
                       boolean active, StorageLevel storageLevel) {
        this.id = id;
        this.code = code;
        this.description = description;
        this.capacity = capacity;
        this.active = active;
        this.storageLevel = storageLevel;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    private String description;

    private Double capacity;

    private boolean active = true;

    @ManyToOne(optional = false)
    private StorageLevel storageLevel;

    @jakarta.persistence.Version
    private Long version;

    @OneToMany(mappedBy = "binLocation")
    @JsonIgnore
    private java.util.List<StockPosition> stockPositions = new java.util.ArrayList<>();

    public Warehouse getWarehouse() {
        return storageLevel.getRack().getAisle().getZone().getWarehouse();
    }
}