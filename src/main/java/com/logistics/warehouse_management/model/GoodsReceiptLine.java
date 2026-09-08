package com.logistics.warehouse_management.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
public class GoodsReceiptLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(optional = false)
    private GoodsReceipt goodsReceipt;

    @ManyToOne(optional = false)
    private InventoryItem inventoryItem;

    private Integer expectedQuantity;

    private Integer receivedQuantity;

    @ManyToOne(optional = false)
    private BinLocation binLocation;

    public int getDifference() {
        return (receivedQuantity == null ? 0 : receivedQuantity)
                - (expectedQuantity == null ? 0 : expectedQuantity);
    }
}