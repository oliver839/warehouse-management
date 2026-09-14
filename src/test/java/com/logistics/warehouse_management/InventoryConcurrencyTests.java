package com.logistics.warehouse_management;

import com.logistics.warehouse_management.controller.InventoryAdjustmentRequest;
import com.logistics.warehouse_management.model.Aisle;
import com.logistics.warehouse_management.model.BinLocation;
import com.logistics.warehouse_management.model.InventoryTransaction;
import com.logistics.warehouse_management.model.InventoryTransactionReason;
import com.logistics.warehouse_management.model.Rack;
import com.logistics.warehouse_management.model.StockPosition;
import com.logistics.warehouse_management.model.StockStatus;
import com.logistics.warehouse_management.model.StorageLevel;
import com.logistics.warehouse_management.model.Tool;
import com.logistics.warehouse_management.model.Warehouse;
import com.logistics.warehouse_management.model.WarehouseZone;
import com.logistics.warehouse_management.repository.InventoryItemRepository;
import com.logistics.warehouse_management.repository.InventoryTransactionRepository;
import com.logistics.warehouse_management.repository.StockPositionRepository;
import com.logistics.warehouse_management.repository.WarehouseRepository;
import com.logistics.warehouse_management.service.InventoryAdjustmentService;
import com.logistics.warehouse_management.service.InventoryService;
import com.logistics.warehouse_management.service.StorageLocationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {"shipping.outbox.interval-ms=3600000"})
class InventoryConcurrencyTests {

    @Autowired
    private WarehouseRepository warehouseRepository;
    @Autowired
    private InventoryItemRepository itemRepository;
    @Autowired
    private StockPositionRepository positionRepository;
    @Autowired
    private InventoryTransactionRepository transactionRepository;
    @Autowired
    private StorageLocationService locationService;
    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private InventoryAdjustmentService adjustmentService;

    @Test
    void approveWritesPositionExactlyOnce() {
        Fixture f = fixture("ADJ-ONCE-");
        StockPosition pos = positionRepository.findByInventoryItemId(f.item.getId()).get(0);
        int txBefore = transactionRepository.findByInventoryItemIdOrderByTimestampDesc(f.item.getId()).size();

        var adjustment = adjustmentService.request(
                new InventoryAdjustmentRequest(pos.getId(), 7, "test", "note"));
        adjustmentService.approve(adjustment.getId());

        StockPosition stored = positionRepository.findById(pos.getId()).orElseThrow();
        assertEquals(7, stored.getQuantity());
        List<InventoryTransaction> tx = transactionRepository
                .findByInventoryItemIdOrderByTimestampDesc(f.item.getId());
        // Exactly one audit transaction for the approval (single write path).
        assertEquals(txBefore + 1, tx.size());
        assertEquals(InventoryTransactionReason.MANUAL_ADJUSTMENT, tx.get(0).getReason());
    }

    @Test
    void overReservationIsRejectedAtomically() {
        Fixture f = fixture("ATOMIC-");
        com.logistics.warehouse_management.model.InventoryItem item =
                itemRepository.findById(f.item.getId()).orElseThrow();
        try {
            inventoryService.reserve(item, 999);
        } catch (IllegalStateException expected) {
        }
        // No partial reservation leaked.
        assertEquals(0, inventoryService.reservedStock(itemRepository.findById(f.item.getId()).orElseThrow()));
        assertEquals(10, inventoryService.totalStock(itemRepository.findById(f.item.getId()).orElseThrow()));
    }

    private Fixture fixture(String prefix) {
        Warehouse warehouse = warehouseRepository.save(
                new Warehouse(null, prefix + "Lager", "Testort", 100.0, null));
        WarehouseZone zone = locationService.saveZone(new WarehouseZone(null, prefix + "Z", "Z", warehouse));
        Aisle aisle = locationService.saveAisle(new Aisle(null, prefix + "A", "A", zone));
        Rack rack = locationService.saveRack(new Rack(null, prefix + "R", "R", aisle));
        StorageLevel level = locationService.saveLevel(new StorageLevel(null, prefix + "L", "L", rack));
        BinLocation bin = locationService.saveBin(
                new BinLocation(null, prefix + "BIN", "Fach", 100.0, true, level));
        Tool item = new Tool();
        item.setSku(prefix + UUID());
        item.setName("Testartikel");
        item.setSpacePerUnit(0.1);
        item.setWarehouse(warehouse);
        item.getStockPositions().add(new StockPosition(null, item, null, null, null, bin,
                10, 0, StockStatus.AVAILABLE, null));
        item = itemRepository.save(item);
        return new Fixture(item);
    }

    private static String UUID() {
        return java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private record Fixture(Tool item) {
    }
}
