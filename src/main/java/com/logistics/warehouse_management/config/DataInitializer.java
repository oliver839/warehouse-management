package com.logistics.warehouse_management.config;

import com.logistics.warehouse_management.model.ConsumableMaterial;
import com.logistics.warehouse_management.model.Tool;
import com.logistics.warehouse_management.model.Warehouse;
import com.logistics.warehouse_management.repository.InventoryItemRepository;
import com.logistics.warehouse_management.repository.WarehouseRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final WarehouseRepository warehouseRepository;
    private final InventoryItemRepository inventoryItemRepository;

    public DataInitializer(WarehouseRepository warehouseRepository,
                           InventoryItemRepository inventoryItemRepository) {
        this.warehouseRepository = warehouseRepository;
        this.inventoryItemRepository = inventoryItemRepository;
    }

    @Override
    public void run(String... args) {
        if (warehouseRepository.count() > 0) {
            return;
        }

        Warehouse mainWarehouse = new Warehouse();
        mainWarehouse.setName("Hauptlager München");
        mainWarehouse.setLocation("München");
        mainWarehouse.setMaxSpace(100.0);
        mainWarehouse = warehouseRepository.save(mainWarehouse);

        Warehouse secondaryWarehouse = new Warehouse();
        secondaryWarehouse.setName("Nebenlager Berlin");
        secondaryWarehouse.setLocation("Berlin");
        secondaryWarehouse.setMaxSpace(50.0);
        secondaryWarehouse = warehouseRepository.save(secondaryWarehouse);

        Tool drill = new Tool();
        drill.setName("Akkubohrer");
        drill.setQuantityInStock(15);
        drill.setSerialNumber("SN-1234");
        drill.setSpacePerUnit(0.5);
        drill.setWarehouse(mainWarehouse);

        ConsumableMaterial screws = new ConsumableMaterial();
        screws.setName("Schrauben M5");
        screws.setQuantityInStock(5_000);
        screws.setMaterialType("Stahl");
        screws.setUnit("Stück");
        screws.setSpacePerUnit(0.001);
        screws.setWarehouse(secondaryWarehouse);

        inventoryItemRepository.save(drill);
        inventoryItemRepository.save(screws);
    }
}
