package com.logistics.warehouse_management.controller;

import com.logistics.warehouse_management.model.InventoryItem;
import com.logistics.warehouse_management.model.ConsumableMaterial;
import com.logistics.warehouse_management.model.Tool;
import com.logistics.warehouse_management.model.Warehouse;
import com.logistics.warehouse_management.model.BinLocation;
import com.logistics.warehouse_management.model.InventoryTransactionReason;
import com.logistics.warehouse_management.repository.InventoryItemRepository;
import com.logistics.warehouse_management.repository.WarehouseRepository;
import com.logistics.warehouse_management.service.WarehouseService;
import com.logistics.warehouse_management.service.StorageLocationService;
import com.logistics.warehouse_management.service.InventoryService;
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
    private final StorageLocationService storageLocationService;
    private final InventoryService inventoryService;

    public ItemController(InventoryItemRepository inventoryItemRepository,
                          WarehouseRepository warehouseRepository,
                           WarehouseService warehouseService,
                           StorageLocationService storageLocationService,
                           InventoryService inventoryService) {
        this.inventoryItemRepository = inventoryItemRepository;
        this.warehouseRepository = warehouseRepository;
        this.warehouseService = warehouseService;
        this.storageLocationService = storageLocationService;
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public List<InventoryItem> getAllItems() {
        return inventoryItemRepository.findAll();
    }

    @PostMapping
    public ResponseEntity<?> createItem(@RequestBody ItemRequest itemRequest) {
        ResponseEntity<String> identityError = validateIdentity(itemRequest, null);
        if (identityError != null) {
            return identityError;
        }
        ResponseEntity<String> capacityError = validateCapacity(itemRequest, itemRequest.quantityInStock());
        if (capacityError != null) {
            return capacityError;
        }
        InventoryItem item = createItemByType(itemRequest.type());
        applyRequest(item, itemRequest);
        item.setQuantityInStock(0);
        item = inventoryItemRepository.save(item);
        inventoryService.adjustStock(item, itemRequest.quantityInStock(),
            InventoryTransactionReason.MANUAL_ADJUSTMENT, "ITEM", item.getId());
        return ResponseEntity.ok(item);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateItem(@PathVariable Long id, @RequestBody ItemRequest itemRequest) {
        InventoryItem item = inventoryItemRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item nicht gefunden"));

        ResponseEntity<String> identityError = validateIdentity(itemRequest, id);
        if (identityError != null) {
            return identityError;
        }
        int requestedQuantity = itemRequest.quantityInStock() == null ? 0 : itemRequest.quantityInStock();
        boolean remainsInSameWarehouse = item.getWarehouse() != null
                && item.getWarehouse().getId().equals(itemRequest.warehouseId());
        ResponseEntity<String> capacityError = validateCapacity(itemRequest, item, requestedQuantity,
                remainsInSameWarehouse);
        if (capacityError != null) {
            return capacityError;
        }
        applyRequest(item, itemRequest);
        item = inventoryItemRepository.save(item);
        inventoryService.adjustStock(item, requestedQuantity,
            InventoryTransactionReason.MANUAL_ADJUSTMENT, "ITEM", item.getId());
        return ResponseEntity.ok(item);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItem(@PathVariable Long id) {
        if (!inventoryItemRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item nicht gefunden");
        }
        inventoryItemRepository.deleteById(id);
    }

    @PutMapping("/{id}/bin-location")
    public ResponseEntity<?> moveItem(@PathVariable Long id, @RequestBody BinLocationRequest request) {
        if (request.binLocationId() == null) {
            return ResponseEntity.badRequest().body("Ein Lagerplatz ist erforderlich.");
        }
        storageLocationService.moveItem(id, request.binLocationId());
        return ResponseEntity.ok(inventoryItemRepository.findById(id).orElseThrow());
    }

    private InventoryItem createItemByType(String type) {
        return "CONSUMABLE".equalsIgnoreCase(type) || "ConsumableMaterial".equalsIgnoreCase(type)
                ? new ConsumableMaterial()
                : new Tool();
    }

    private void applyRequest(InventoryItem item, ItemRequest request) {
        Warehouse warehouse = warehouseRepository.findById(request.warehouseId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lager nicht gefunden"));

        item.setSku(request.sku().trim());
        item.setBarcode(normalizeOptionalValue(request.barcode()));
        item.setName(request.name());
        item.setSpacePerUnit(request.spacePerUnit());
        item.setWeightPerUnit(request.weightPerUnit());
        item.setLength(request.length());
        item.setWidth(request.width());
        item.setHeight(request.height());
        item.setWarehouse(warehouse);

        if (request.binLocationId() != null) {
            BinLocation bin = storageLocationService.getActiveBin(request.binLocationId());
            if (!warehouse.getId().equals(bin.getWarehouse().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Der Lagerplatz gehört zu einem anderen Lager.");
            }
            storageLocationService.ensureCapacity(bin, item,
                request.quantityInStock() == null ? 0 : request.quantityInStock());
            item.setBinLocation(bin);
        }

        if (item instanceof Tool tool) {
            tool.setSerialNumber(request.serialNumber());
            tool.setCalibrated(Boolean.TRUE.equals(request.isCalibrated()));
        }
        if (item instanceof ConsumableMaterial material) {
            material.setMaterialType(request.materialType());
            material.setUnit(request.unit());
        }
    }

    private ResponseEntity<String> validateIdentity(ItemRequest request, Long currentItemId) {
        if (request.sku() == null || request.sku().isBlank()) {
            return ResponseEntity.badRequest().body("Eine SKU ist erforderlich.");
        }
        String sku = request.sku().trim();
        String barcode = normalizeOptionalValue(request.barcode());
        boolean duplicateSku = currentItemId == null
                ? inventoryItemRepository.existsBySkuIgnoreCase(sku)
                : inventoryItemRepository.existsBySkuIgnoreCaseAndIdNot(sku, currentItemId);
        if (duplicateSku) {
            return ResponseEntity.badRequest().body("Die SKU ist bereits vergeben.");
        }
        boolean duplicateBarcode = barcode != null && (currentItemId == null
                ? inventoryItemRepository.existsByBarcode(barcode)
                : inventoryItemRepository.existsByBarcodeAndIdNot(barcode, currentItemId));
        if (duplicateBarcode) {
            return ResponseEntity.badRequest().body("Der Barcode ist bereits vergeben.");
        }
        return null;
    }

    private String normalizeOptionalValue(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
