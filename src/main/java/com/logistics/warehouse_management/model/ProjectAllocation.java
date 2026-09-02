package com.logistics.warehouse_management.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class ProjectAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer allocatedQuantity;

    private LocalDate allocationDate;

    @Enumerated(EnumType.STRING)
    private AllocationStatus status;

    @ManyToOne
    private Project project;

    @ManyToOne
    private Warehouse warehouse;

    @ManyToOne
    private InventoryItem inventoryItem;

    @OneToOne
    private DeliveryNote deliveryNote;
}
