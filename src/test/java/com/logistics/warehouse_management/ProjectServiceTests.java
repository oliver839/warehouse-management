package com.logistics.warehouse_management;

import com.logistics.warehouse_management.model.Project;
import com.logistics.warehouse_management.model.ProjectAllocation;
import com.logistics.warehouse_management.model.ProjectStatus;
import com.logistics.warehouse_management.model.Tool;
import com.logistics.warehouse_management.model.Warehouse;
import com.logistics.warehouse_management.repository.InventoryItemRepository;
import com.logistics.warehouse_management.repository.ProjectAllocationRepository;
import com.logistics.warehouse_management.repository.ProjectRepository;
import com.logistics.warehouse_management.repository.WarehouseRepository;
import com.logistics.warehouse_management.service.ProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Transactional
class ProjectServiceTests {

    @Autowired
    private WarehouseRepository warehouseRepository;
    @Autowired
    private InventoryItemRepository inventoryItemRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectAllocationRepository projectAllocationRepository;
    @Autowired
    private ProjectService projectService;

    @Test
    void reservesAndConsumesAllocatedInventory() {
        Warehouse warehouse = new Warehouse();
        warehouse.setName("Testlager");
        warehouse.setLocation("Testort");
        warehouse.setMaxSpace(100.0);
        warehouse = warehouseRepository.save(warehouse);

        Tool screws = new Tool();
        screws.setName("Schrauben");
        screws.setQuantityInStock(100);
        screws.setReservedQuantity(0);
        screws.setSpacePerUnit(0.001);
        screws.setWarehouse(warehouse);
        screws = inventoryItemRepository.save(screws);

        Project project = new Project();
        project.setName("Reparatur Maschine 1");
        project.setDescription("Testauftrag");
        project = projectRepository.save(project);

        ProjectAllocation allocation = new ProjectAllocation();
        allocation.setProject(project);
        allocation.setInventoryItem(screws);
        allocation.setAllocatedQuantity(20);
        projectAllocationRepository.save(allocation);

        projectService.changeStatus(project.getId(), ProjectStatus.APPROVED);
        Tool reservedScrews = (Tool) inventoryItemRepository.findById(screws.getId()).orElseThrow();
        assertEquals(20, reservedScrews.getReservedQuantity());
        assertEquals(80, reservedScrews.getAvailableQuantity());

        projectService.changeStatus(project.getId(), ProjectStatus.COMPLETED);
        Tool consumedScrews = (Tool) inventoryItemRepository.findById(screws.getId()).orElseThrow();
        assertEquals(80, consumedScrews.getQuantityInStock());
        assertEquals(0, consumedScrews.getReservedQuantity());
        assertEquals(80, consumedScrews.getAvailableQuantity());
    }
}
