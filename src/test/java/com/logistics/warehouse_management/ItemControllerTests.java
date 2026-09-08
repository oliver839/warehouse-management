package com.logistics.warehouse_management;

import com.logistics.warehouse_management.controller.ItemController;
import com.logistics.warehouse_management.controller.ItemRequest;
import com.logistics.warehouse_management.model.Warehouse;
import com.logistics.warehouse_management.repository.WarehouseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class ItemControllerTests {

    @Autowired
        private ItemController itemController;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Test
        void rejectsDuplicateSkuAndBarcode() {
        Warehouse warehouse = warehouseRepository.findAll().get(0);
                ItemRequest firstItem = itemRequest("TEST-IDENTITY-001", "4900000000001", warehouse.getId());
                ItemRequest duplicateSku = itemRequest("TEST-IDENTITY-001", "4900000000002", warehouse.getId());
                ItemRequest duplicateBarcode = itemRequest("TEST-IDENTITY-002", "4900000000001", warehouse.getId());

                assertEquals(200, itemController.createItem(firstItem).getStatusCode().value());
                assertError(itemController.createItem(duplicateSku), "SKU ist bereits vergeben");
                assertError(itemController.createItem(duplicateBarcode), "Barcode ist bereits vergeben");
    }

    @Test
        void requiresSku() {
        Warehouse warehouse = warehouseRepository.findAll().get(0);
                assertError(itemController.createItem(itemRequest("", "4900000000003", warehouse.getId())), "SKU ist erforderlich");
    }

        private ItemRequest itemRequest(String sku, String barcode, Long warehouseId) {
                return new ItemRequest(sku, barcode, "Testartikel", 10, warehouseId, 0.1,
                    null, null, null, null, "TOOL", null, false, null, null, null);
        }

        private void assertError(org.springframework.http.ResponseEntity<?> response, String message) {
                assertEquals(400, response.getStatusCode().value());
                assertTrue(String.valueOf(response.getBody()).contains(message));
    }
}
