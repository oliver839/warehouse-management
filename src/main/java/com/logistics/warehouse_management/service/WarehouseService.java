package com.logistics.warehouse_management.service;

import com.logistics.warehouse_management.model.InventoryItem;
import com.logistics.warehouse_management.model.Warehouse;
import com.logistics.warehouse_management.repository.InventoryItemRepository;
import com.logistics.warehouse_management.repository.WarehouseRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final InventoryItemRepository inventoryItemRepository;

    public WarehouseService(WarehouseRepository warehouseRepository,
                            InventoryItemRepository inventoryItemRepository) {
        this.warehouseRepository = warehouseRepository;
        this.inventoryItemRepository = inventoryItemRepository;
    }

    public boolean hasEnoughCapacity(Long warehouseId, int addedQuantity, double spacePerUnit) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                        "Lager nicht gefunden"));

        double usedSpace = inventoryItemRepository.findByWarehouseId(warehouseId).stream()
                .mapToDouble(this::spaceUsedByItem)
                .sum();
        double maximumSpace = warehouse.getMaxSpace() == null ? 0.0 : warehouse.getMaxSpace();

        return usedSpace + (addedQuantity * spacePerUnit) <= maximumSpace;
    }

    public boolean hasEnoughCapacityAfterUpdate(Long warehouseId, InventoryItem currentItem,
                                                int newQuantity, double newSpacePerUnit) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                        "Lager nicht gefunden"));

        double usedSpaceWithoutCurrentItem = inventoryItemRepository.findByWarehouseId(warehouseId).stream()
                .filter(item -> !item.getId().equals(currentItem.getId()))
                .mapToDouble(this::spaceUsedByItem)
                .sum();
        double maximumSpace = warehouse.getMaxSpace() == null ? 0.0 : warehouse.getMaxSpace();

        return usedSpaceWithoutCurrentItem + (newQuantity * newSpacePerUnit) <= maximumSpace;
    }

    private double spaceUsedByItem(InventoryItem item) {
        int quantity = item.getQuantityInStock() == null ? 0 : item.getQuantityInStock();
        double itemSpacePerUnit = item.getSpacePerUnit() == null ? 0.0 : item.getSpacePerUnit();
        return quantity * itemSpacePerUnit;
    }
}
