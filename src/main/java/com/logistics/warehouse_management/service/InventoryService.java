package com.logistics.warehouse_management.service;

import com.logistics.warehouse_management.model.InventoryItem;
import com.logistics.warehouse_management.repository.InventoryItemRepository;
import org.springframework.stereotype.Service;

@Service
public class InventoryService {

    private final InventoryItemRepository inventoryItemRepository;

    public InventoryService(InventoryItemRepository inventoryItemRepository) {
        this.inventoryItemRepository = inventoryItemRepository;
    }

    public void reserve(InventoryItem item, int quantity) {
        if (quantity > item.getAvailableQuantity()) {
            throw new IllegalStateException("Nicht genug Bestand verfügbar");
        }
        item.setReservedQuantity(getReservedQuantity(item) + quantity);
        inventoryItemRepository.save(item);
    }

    public void releaseReservation(InventoryItem item, int quantity) {
        item.setReservedQuantity(Math.max(0, getReservedQuantity(item) - quantity));
        inventoryItemRepository.save(item);
    }

    public void consumeReservedStock(InventoryItem item, int quantity) {
        int stock = item.getQuantityInStock() == null ? 0 : item.getQuantityInStock();
        if (quantity > getReservedQuantity(item) || quantity > stock) {
            throw new IllegalStateException("Reservierter Bestand ist nicht verfügbar");
        }
        item.setQuantityInStock(stock - quantity);
        item.setReservedQuantity(getReservedQuantity(item) - quantity);
        inventoryItemRepository.save(item);
    }

    private int getReservedQuantity(InventoryItem item) {
        return item.getReservedQuantity() == null ? 0 : item.getReservedQuantity();
    }
}
