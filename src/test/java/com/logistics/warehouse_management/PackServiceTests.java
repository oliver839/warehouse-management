package com.logistics.warehouse_management;

import com.logistics.warehouse_management.controller.PickScanRequest;
import com.logistics.warehouse_management.model.Aisle;
import com.logistics.warehouse_management.model.BinLocation;
import com.logistics.warehouse_management.model.DeliveryNote;
import com.logistics.warehouse_management.model.PackStatus;
import com.logistics.warehouse_management.model.Project;
import com.logistics.warehouse_management.model.ProjectAllocation;
import com.logistics.warehouse_management.model.ProjectStatus;
import com.logistics.warehouse_management.model.Rack;
import com.logistics.warehouse_management.model.StorageLevel;
import com.logistics.warehouse_management.model.Tool;
import com.logistics.warehouse_management.model.Warehouse;
import com.logistics.warehouse_management.model.WarehouseZone;
import com.logistics.warehouse_management.repository.InventoryItemRepository;
import com.logistics.warehouse_management.repository.ProjectAllocationRepository;
import com.logistics.warehouse_management.repository.ProjectRepository;
import com.logistics.warehouse_management.repository.WarehouseRepository;
import com.logistics.warehouse_management.service.PackService;
import com.logistics.warehouse_management.service.PickOrderService;
import com.logistics.warehouse_management.service.ProjectService;
import com.logistics.warehouse_management.service.StorageLocationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class PackServiceTests {

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
    private PackService packService;

    @Test
    void packsOnceAndDeductsStockExactlyOnce() {
        Warehouse warehouse = warehouseRepository.save(new Warehouse(null, "Packlager", "Testort", 100.0, null));
        WarehouseZone zone = locationService.saveZone(new WarehouseZone(null, "PACK-ZONE", "Packbereich", warehouse));
        Aisle aisle = locationService.saveAisle(new Aisle(null, "PACK-AISLE", "Packgang", zone));
        Rack rack = locationService.saveRack(new Rack(null, "PACK-RACK", "Packregal", aisle));
        StorageLevel level = locationService.saveLevel(new StorageLevel(null, "PACK-LEVEL", "Packebene", rack));
        BinLocation bin = locationService.saveBin(new BinLocation(null, "P-01-01", "Packfach", 10.0, true, level));

        Tool item = new Tool();
        item.setSku("PACK-TEST-001");
        item.setBarcode("4900000000088");
        item.setName("Packartikel");
        item.setQuantityInStock(5);
        item.setSpacePerUnit(0.1);
        item.setWarehouse(warehouse);
        item.setBinLocation(bin);
        item = itemRepository.save(item);

        Project project = new Project();
        project.setName("Packauftrag Test");
        project.setCustomerName("Testempfaenger");
        project.setDeliveryAddress("Testadresse 1");
        project.setStatus(ProjectStatus.PENDING);
        project = projectRepository.save(project);

        ProjectAllocation allocation = new ProjectAllocation();
        allocation.setProject(project);
        allocation.setInventoryItem(item);
        allocation.setAllocatedQuantity(2);
        allocationRepository.save(allocation);

        projectService.changeStatus(project.getId(), ProjectStatus.APPROVED);
        var pickOrder = pickOrderService.create(project.getId());
        pickOrderService.start(pickOrder.getId());
        Long lineId = pickOrderService.getLines(pickOrder.getId()).get(0).getId();
        pickOrderService.scan(pickOrder.getId(), lineId, new PickScanRequest("4900000000088", 2));
        pickOrderService.complete(pickOrder.getId());

        DeliveryNote note = packService.pack(pickOrder.getId());
        assertEquals(PackStatus.PACKED, note.getPackStatus());
        assertEquals(3, itemRepository.findById(item.getId()).orElseThrow().getQuantityInStock());
        assertThrows(ResponseStatusException.class, () -> packService.pack(pickOrder.getId()));
        assertEquals(3, itemRepository.findById(item.getId()).orElseThrow().getQuantityInStock());
    }
}
