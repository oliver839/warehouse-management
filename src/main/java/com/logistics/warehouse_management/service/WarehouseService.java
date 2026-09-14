package com.logistics.warehouse_management.service;

import com.logistics.warehouse_management.model.InventoryItem;
import com.logistics.warehouse_management.model.Warehouse;
import com.logistics.warehouse_management.repository.InventoryItemRepository;
import com.logistics.warehouse_management.repository.WarehouseRepository;
import com.logistics.warehouse_management.repository.StockPositionRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final StockPositionRepository stockPositionRepository;

    public WarehouseService(WarehouseRepository warehouseRepository,
                            InventoryItemRepository inventoryItemRepository,
                            StockPositionRepository stockPositionRepository) {
        this.warehouseRepository = warehouseRepository;
        this.inventoryItemRepository = inventoryItemRepository;
        this.stockPositionRepository = stockPositionRepository;
    }

    public boolean hasEnoughCapacity(Long warehouseId, int addedQuantity, double spacePerUnit) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                        "Lager nicht gefunden"));

        double usedSpace = stockPositionRepository.findAll().stream()
            .filter(position -> position.getBinLocation() != null && position.getBinLocation().getWarehouse().getId().equals(warehouseId))
            .mapToDouble(position -> spaceUsedByItem(position.getInventoryItem(), position.getQuantity()))
                .sum();
        double maximumSpace = warehouse.getMaxSpace() == null ? 0.0 : warehouse.getMaxSpace();

        return usedSpace + (addedQuantity * spacePerUnit) <= maximumSpace;
    }

    public boolean hasEnoughCapacityAfterUpdate(Long warehouseId, InventoryItem currentItem,
                                                int newQuantity, double newSpacePerUnit) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                        "Lager nicht gefunden"));

        double usedSpaceWithoutCurrentItem = stockPositionRepository.findAll().stream()
            .filter(position -> position.getBinLocation() != null && position.getBinLocation().getWarehouse().getId().equals(warehouseId))
            .filter(position -> !position.getInventoryItem().getId().equals(currentItem.getId()))
            .mapToDouble(position -> spaceUsedByItem(position.getInventoryItem(), position.getQuantity()))
                .sum();
        double maximumSpace = warehouse.getMaxSpace() == null ? 0.0 : warehouse.getMaxSpace();

        return usedSpaceWithoutCurrentItem + (newQuantity * newSpacePerUnit) <= maximumSpace;
    }

    private double spaceUsedByItem(InventoryItem item, Integer positionQuantity) {
        int quantity = positionQuantity == null ? 0 : positionQuantity;
        double itemSpacePerUnit = item.getSpacePerUnit() == null ? 0.0 : item.getSpacePerUnit();
        return quantity * itemSpacePerUnit;
    }
}
