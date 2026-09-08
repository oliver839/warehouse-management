package com.logistics.warehouse_management;

import com.logistics.warehouse_management.repository.InventoryItemRepository;
import com.logistics.warehouse_management.service.CsvImportException;
import com.logistics.warehouse_management.service.CsvImportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class CsvImportServiceTests {

    @Autowired
    private CsvImportService csvImportService;

    @Autowired
    private InventoryItemRepository itemRepository;

    @Test
    void importsValidItems() {
        MockMultipartFile valid = new MockMultipartFile("file", "items.csv", "text/csv",
                "sku,barcode,name,quantityInStock,spacePerUnit,warehouseId,type\nCSV-TEST-001,4900000000077,CSV Artikel,4,0.1,1,TOOL\n".getBytes());
        assertEquals(1, csvImportService.importItems(valid));
        assertEquals(1, itemRepository.findBySkuIgnoreCase("CSV-TEST-001").stream().count());

    }

    @Test
    void rejectsInvalidRowsAtomically() {
        MockMultipartFile invalid = new MockMultipartFile("file", "invalid.csv", "text/csv",
                "sku,barcode,name,quantityInStock,spacePerUnit,warehouseId,type\nCSV-TEST-002,,Fehler 1,4,0.1,1,TOOL\nCSV-TEST-002,,Fehler 2,4,0.1,1,TOOL\n".getBytes());
        assertThrows(CsvImportException.class, () -> csvImportService.importItems(invalid));
        assertEquals(0, itemRepository.findBySkuIgnoreCase("CSV-TEST-002").stream().count());
    }
}
