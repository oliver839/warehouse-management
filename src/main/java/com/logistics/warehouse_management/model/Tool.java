package com.logistics.warehouse_management.model;

import jakarta.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
public class Tool extends InventoryItem {

    private String serialNumber;

    private LocalDate maintenanceDate;

    private boolean isCalibrated;
}
