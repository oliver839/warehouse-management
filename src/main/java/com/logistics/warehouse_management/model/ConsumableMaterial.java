package com.logistics.warehouse_management.model;

import jakarta.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
public class ConsumableMaterial extends InventoryItem {

    private Double length;

    private String materialType;

    private String unit;
}
