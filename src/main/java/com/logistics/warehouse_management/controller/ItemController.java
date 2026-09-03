package com.logistics.warehouse_management.controller;

import com.logistics.warehouse_management.model.InventoryItem;
import com.logistics.warehouse_management.model.ConsumableMaterial;
import com.logistics.warehouse_management.model.Tool;
import com.logistics.warehouse_management.model.Warehouse;
import com.logistics.warehouse_management.repository.InventoryItemRepository;
import com.logistics.warehouse_management.repository.WarehouseRepository;
import com.logistics.warehouse_management.service.WarehouseService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/items")
public class ItemController {

    private final InventoryItemRepository inventoryItemRepository;
    private final WarehouseRepository warehouseRepository;
    private final WarehouseService warehouseService;

    public ItemController(InventoryItemRepository inventoryItemRepository,
                          WarehouseRepository warehouseRepository,
                          WarehouseService warehouseService) {
        this.inventoryItemRepository = inventoryItemRepository;
        this.warehouseRepository = warehouseRepository;
        this.warehouseService = warehouseService;
    }

    @GetMapping
    public List<InventoryItem> getAllItems() {
        return inventoryItemRepository.findAll();
    }

    @PostMapping
    public ResponseEntity<?> createItem(@RequestBody ItemRequest itemRequest) {
        ResponseEntity<String> capacityError = validateCapacity(itemRequest, itemRequest.quantityInStock());
        if (capacityError != null) {
            return capacityError;
        }
        InventoryItem item = createItemByType(itemRequest.type());
        applyRequest(item, itemRequest);
        return ResponseEntity.ok(inventoryItemRepository.save(item));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateItem(@PathVariable Long id, @RequestBody ItemRequest itemRequest) {
        InventoryItem item = inventoryItemRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item nicht gefunden"));

        int requestedQuantity = itemRequest.quantityInStock() == null ? 0 : itemRequest.quantityInStock();
        boolean remainsInSameWarehouse = item.getWarehouse() != null
                && item.getWarehouse().getId().equals(itemRequest.warehouseId());
        ResponseEntity<String> capacityError = validateCapacity(itemRequest, item, requestedQuantity,
                remainsInSameWarehouse);
        if (capacityError != null) {
            return capacityError;
        }
        applyRequest(item, itemRequest);
        return ResponseEntity.ok(inventoryItemRepository.save(item));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItem(@PathVariable Long id) {
        if (!inventoryItemRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item nicht gefunden");
        }
        inventoryItemRepository.deleteById(id);
    }

    private InventoryItem createItemByType(String type) {
        return "CONSUMABLE".equalsIgnoreCase(type) || "ConsumableMaterial".equalsIgnoreCase(type)
                ? new ConsumableMaterial()
                : new Tool();
    }

    private void applyRequest(InventoryItem item, ItemRequest request) {
        Warehouse warehouse = warehouseRepository.findById(request.warehouseId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lager nicht gefunden"));

        item.setName(request.name());
        item.setQuantityInStock(request.quantityInStock());
        item.setSpacePerUnit(request.spacePerUnit());
        item.setWarehouse(warehouse);

        if (item instanceof Tool tool) {
            tool.setSerialNumber(request.serialNumber());
            tool.setCalibrated(Boolean.TRUE.equals(request.isCalibrated()));
        }
        if (item instanceof ConsumableMaterial material) {
            material.setMaterialType(request.materialType());
            material.setUnit(request.unit());
        }
    }

    private ResponseEntity<String> validateCapacity(ItemRequest request, int addedQuantity) {
        return validateCapacity(request, null, addedQuantity, false);
    }

    private ResponseEntity<String> validateCapacity(ItemRequest request, InventoryItem currentItem,
                                                    int requestedQuantity, boolean replacesCurrentItem) {
        if (request.warehouseId() == null || request.quantityInStock() == null || request.spacePerUnit() == null) {
            return ResponseEntity.badRequest().body("Lager, Menge und Platzbedarf pro Stück sind erforderlich.");
        }
        if (request.quantityInStock() < 0 || request.spacePerUnit() < 0) {
            return ResponseEntity.badRequest().body("Menge und Platzbedarf dürfen nicht negativ sein.");
        }
        boolean hasEnoughCapacity = replacesCurrentItem
                ? warehouseService.hasEnoughCapacityAfterUpdate(request.warehouseId(), currentItem,
                requestedQuantity, request.spacePerUnit())
                : warehouseService.hasEnoughCapacity(request.warehouseId(), requestedQuantity, request.spacePerUnit());
        if (!hasEnoughCapacity) {
            return ResponseEntity.badRequest().body("Nicht genügend freier Lagerplatz für dieses Item verfügbar.");
        }
        return null;
    }
}
