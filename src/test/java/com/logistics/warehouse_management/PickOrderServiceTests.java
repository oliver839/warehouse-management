package com.logistics.warehouse_management;

import com.logistics.warehouse_management.controller.PickScanRequest;
import com.logistics.warehouse_management.model.Aisle;
import com.logistics.warehouse_management.model.BinLocation;
import com.logistics.warehouse_management.model.PickLineStatus;
import com.logistics.warehouse_management.model.PickOrder;
import com.logistics.warehouse_management.model.PickOrderLine;
import com.logistics.warehouse_management.model.Project;
import com.logistics.warehouse_management.model.ProjectAllocation;
import com.logistics.warehouse_management.model.ProjectStatus;
import com.logistics.warehouse_management.model.Rack;
import com.logistics.warehouse_management.model.StorageLevel;
import com.logistics.warehouse_management.model.Tool;
import com.logistics.warehouse_management.model.Warehouse;
import com.logistics.warehouse_management.model.WarehouseZone;
import com.logistics.warehouse_management.repository.InventoryItemRepository;
import com.logistics.warehouse_management.repository.PickScanRepository;
import com.logistics.warehouse_management.repository.ProjectAllocationRepository;
import com.logistics.warehouse_management.repository.ProjectRepository;
import com.logistics.warehouse_management.repository.WarehouseRepository;
import com.logistics.warehouse_management.service.PickOrderService;
import com.logistics.warehouse_management.service.ProjectService;
import com.logistics.warehouse_management.service.StorageLocationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class PickOrderServiceTests {

    @Autowired
    private WarehouseRepository warehouseRepository;
    @Autowired
    private InventoryItemRepository itemRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectAllocationRepository allocationRepository;
    @Autowired
    private StorageLocationService locationService;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private PickOrderService pickOrderService;
    @Autowired
    private PickScanRepository scanRepository;

    @Test
    void validatesScansSupportsPartialPickingAndCompletesPickOrder() {
        Warehouse warehouse = warehouseRepository.save(new Warehouse(null, "Picklager", "Testort", 100.0, null));
        WarehouseZone zone = locationService.saveZone(new WarehouseZone(null, "PICK-ZONE", "Pickbereich", warehouse));
        Aisle aisle = locationService.saveAisle(new Aisle(null, "PICK-AISLE", "Pickgang", zone));
        Rack rack = locationService.saveRack(new Rack(null, "PICK-RACK", "Pickregal", aisle));
        StorageLevel level = locationService.saveLevel(new StorageLevel(null, "PICK-LEVEL", "Pickebene", rack));
        BinLocation bin = locationService.saveBin(new BinLocation(null, "A-01-01", "Pickfach", 10.0, true, level));

        Tool item = new Tool();
        item.setSku("PICK-TEST-001");
        item.setBarcode("4900000000099");
        item.setName("Pickartikel");
        item.setSpacePerUnit(0.1);
        item.setWarehouse(warehouse);
        item.getStockPositions().add(new com.logistics.warehouse_management.model.StockPosition(null, item, null, null, null, bin, 10, 0, com.logistics.warehouse_management.model.StockStatus.AVAILABLE, null));
        item = itemRepository.save(item);

        Project project = new Project();
        project.setName("Pickauftrag Test");
        project.setStatus(ProjectStatus.PENDING);
        project = projectRepository.save(project);

        ProjectAllocation allocation = new ProjectAllocation();
        allocation.setProject(project);
        allocation.setInventoryItem(item);
        allocation.setAllocatedQuantity(3);
        allocationRepository.save(allocation);

        projectService.changeStatus(project.getId(), ProjectStatus.APPROVED);
        PickOrder pickOrder = pickOrderService.create(project.getId());
        assertEquals("A-01-01", pickOrderService.getLines(pickOrder.getId()).get(0).getBinLocation().getCode());

        pickOrderService.start(pickOrder.getId());
        Long lineId = pickOrderService.getLines(pickOrder.getId()).get(0).getId();
        assertThrows(ResponseStatusException.class, () -> pickOrderService.scan(
                pickOrder.getId(), lineId, new PickScanRequest("wrong", 1)));
        assertEquals(1, scanRepository.findByPickOrderLineIdOrderByScannedAtDesc(lineId).size());

        PickOrder afterPartial = pickOrderService.scan(pickOrder.getId(), lineId,
                new PickScanRequest("4900000000099", 2));
        assertEquals(PickLineStatus.PARTIALLY_PICKED, pickOrderService.getLines(pickOrder.getId()).get(0).getStatus());

        pickOrderService.scan(pickOrder.getId(), lineId,
                new PickScanRequest("4900000000099", 1));
        PickOrder completed = pickOrderService.complete(pickOrder.getId());
        assertEquals("COMPLETED", completed.getStatus().name());
    }

    @Test
    void createsPickOrderFromBulkStockWithoutBin() {
        // Regression: StockPosition.binLocation is nullable (bulk stock). Previously
        // PickOrderLine.binLocation was NOT NULL, so creating a pick order from a
        // project approved against bulk stock threw DataIntegrityViolationException (500).
        Warehouse warehouse = warehouseRepository.save(new Warehouse(null, "Bulk-Lager", "Testort", 100.0, null));

        Tool item = new Tool();
        item.setSku("BULK-001");
        item.setBarcode("4900000000098");
        item.setName("Bulk-Artikel");
        item.setSpacePerUnit(0.1);
        item.setWarehouse(warehouse);
        // Bulk stock: StockPosition with NO binLocation (null).
        item.getStockPositions().add(new com.logistics.warehouse_management.model.StockPosition(
                null, item, null, null, null, null, 10, 0,
                com.logistics.warehouse_management.model.StockStatus.AVAILABLE, null));
        item = itemRepository.save(item);

        Project project = new Project();
        project.setName("Bulk-Auftrag");
        project.setStatus(ProjectStatus.PENDING);
        project = projectRepository.save(project);

        ProjectAllocation allocation = new ProjectAllocation();
        allocation.setProject(project);
        allocation.setInventoryItem(item);
        allocation.setAllocatedQuantity(3);
        allocationRepository.save(allocation);

        projectService.changeStatus(project.getId(), ProjectStatus.APPROVED);

        PickOrder pickOrder = pickOrderService.create(project.getId());

        PickOrderLine line = pickOrderService.getLines(pickOrder.getId()).get(0);
        assertEquals(3, line.getRequiredQuantity());
        assertEquals(3, line.getStockPosition().getReservedQuantity());
        assertEquals(null, line.getBinLocation(),
                "Bulk stock (no bin) should map to a null bin on the pick line");
    }
}
