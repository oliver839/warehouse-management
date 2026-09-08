package com.logistics.warehouse_management;

import com.logistics.warehouse_management.controller.GoodsReceiptLineRequest;
import com.logistics.warehouse_management.model.Aisle;
import com.logistics.warehouse_management.model.BinLocation;
import com.logistics.warehouse_management.model.GoodsReceipt;
import com.logistics.warehouse_management.model.InventoryTransaction;
import com.logistics.warehouse_management.model.InventoryTransactionReason;
import com.logistics.warehouse_management.model.Rack;
import com.logistics.warehouse_management.model.StorageLevel;
import com.logistics.warehouse_management.model.Tool;
import com.logistics.warehouse_management.model.Warehouse;
import com.logistics.warehouse_management.model.WarehouseZone;
import com.logistics.warehouse_management.repository.InventoryItemRepository;
import com.logistics.warehouse_management.repository.InventoryTransactionRepository;
import com.logistics.warehouse_management.repository.WarehouseRepository;
import com.logistics.warehouse_management.service.GoodsReceiptService;
import com.logistics.warehouse_management.service.StorageLocationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Transactional
class GoodsReceiptServiceTests {

    @Autowired
    private WarehouseRepository warehouseRepository;
    @Autowired
    private InventoryItemRepository inventoryItemRepository;
    @Autowired
    private InventoryTransactionRepository transactionRepository;
    @Autowired
    private StorageLocationService locationService;
    @Autowired
    private GoodsReceiptService receiptService;

    @Test
    void confirmsReceiptIntoBinAndWritesAuditTransaction() {
        Warehouse warehouse = new Warehouse(null, "Testlager Eingang", "Testort", 100.0, null);
        warehouse = warehouseRepository.save(warehouse);

        WarehouseZone zone = new WarehouseZone(null, "ZONE-1", "Testbereich", warehouse);
        zone = locationService.saveZone(zone);
        Aisle aisle = locationService.saveAisle(new Aisle(null, "AISLE-1", "Testgang", zone));
        Rack rack = locationService.saveRack(new Rack(null, "RACK-1", "Testregal", aisle));
        StorageLevel level = locationService.saveLevel(new StorageLevel(null, "LEVEL-1", "Ebene 1", rack));
        BinLocation bin = locationService.saveBin(new BinLocation(null, "BIN-1", "Testfach", 10.0, true, level));

        Tool item = new Tool();
        item.setSku("RECEIPT-TEST-001");
        item.setName("Wareneingang Testartikel");
        item.setQuantityInStock(0);
        item.setSpacePerUnit(0.5);
        item.setWarehouse(warehouse);
        item = inventoryItemRepository.save(item);

        GoodsReceipt receipt = receiptService.create("GR-TEST-001", "Testlieferant", "tester");
        receiptService.addLine(receipt.getId(), new GoodsReceiptLineRequest(item.getId(), 4, 3, bin.getId()));
        receiptService.confirm(receipt.getId());

        Tool storedItem = (Tool) inventoryItemRepository.findById(item.getId()).orElseThrow();
        assertEquals(3, storedItem.getQuantityInStock());
        assertEquals(bin.getId(), storedItem.getBinLocation().getId());

        InventoryTransaction transaction = transactionRepository
                .findByInventoryItemIdOrderByTimestampDesc(item.getId()).get(0);
        assertEquals(3, transaction.getQuantityDelta());
        assertEquals(0, transaction.getPreviousQuantity());
        assertEquals(3, transaction.getNewQuantity());
        assertEquals(InventoryTransactionReason.GOODS_RECEIPT, transaction.getReason());
        assertEquals(receipt.getId(), transaction.getReferenceId());
    }
}
