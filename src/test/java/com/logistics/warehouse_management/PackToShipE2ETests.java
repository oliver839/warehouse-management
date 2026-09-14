package com.logistics.warehouse_management;

import com.logistics.warehouse_management.controller.PickScanRequest;
import com.logistics.warehouse_management.model.Aisle;
import com.logistics.warehouse_management.model.BinLocation;
import com.logistics.warehouse_management.model.DeliveryNote;
import com.logistics.warehouse_management.model.Project;
import com.logistics.warehouse_management.model.ProjectAllocation;
import com.logistics.warehouse_management.model.ProjectStatus;
import com.logistics.warehouse_management.model.Rack;
import com.logistics.warehouse_management.model.ShippingOutboxEvent;
import com.logistics.warehouse_management.model.ShippingOutboxStatus;
import com.logistics.warehouse_management.model.ShippingStatus;
import com.logistics.warehouse_management.model.StockPosition;
import com.logistics.warehouse_management.model.StockStatus;
import com.logistics.warehouse_management.model.StorageLevel;
import com.logistics.warehouse_management.model.Tool;
import com.logistics.warehouse_management.model.Warehouse;
import com.logistics.warehouse_management.model.WarehouseZone;
import com.logistics.warehouse_management.repository.DeliveryNoteRepository;
import com.logistics.warehouse_management.repository.InventoryItemRepository;
import com.logistics.warehouse_management.repository.ProjectAllocationRepository;
import com.logistics.warehouse_management.repository.ProjectRepository;
import com.logistics.warehouse_management.repository.ShippingIdempotencyRecordRepository;
import com.logistics.warehouse_management.repository.ShippingOutboxEventRepository;
import com.logistics.warehouse_management.repository.WarehouseRepository;
import com.logistics.warehouse_management.service.PackService;
import com.logistics.warehouse_management.service.PickOrderService;
import com.logistics.warehouse_management.service.ProjectService;
import com.logistics.warehouse_management.service.ShippingOutboxProcessor;
import com.logistics.warehouse_management.service.StorageLocationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Demo-core E2E: real PackService flow (no synthetic 999999 deliveryNoteId).
 * Wareneingang is covered by GoodsReceiptServiceTests; here: approve -&gt; pick -&gt;
 * pack -&gt; outbox -&gt; SHIPPED with tracking, idempotent on re-run.
 */
@SpringBootTest(properties = {"shipping.outbox.interval-ms=3600000"})
class PackToShipE2ETests {

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
    @Autowired
    private ShippingOutboxProcessor processor;
    @Autowired
    private ShippingOutboxEventRepository outboxRepository;
    @Autowired
    private DeliveryNoteRepository deliveryNoteRepository;
    @Autowired
    private ShippingIdempotencyRecordRepository idempotencyRepository;

    @Test
    void packToShipEndToEnd() {
        Warehouse warehouse = warehouseRepository.save(
                new Warehouse(null, "E2E-Lager", "Testort", 100.0, null));
        WarehouseZone zone = locationService.saveZone(new WarehouseZone(null, "E2E-Z", "Z", warehouse));
        Aisle aisle = locationService.saveAisle(new Aisle(null, "E2E-A", "A", zone));
        Rack rack = locationService.saveRack(new Rack(null, "E2E-R", "R", aisle));
        StorageLevel level = locationService.saveLevel(new StorageLevel(null, "E2E-L", "L", rack));
        BinLocation bin = locationService.saveBin(
                new BinLocation(null, "E2E-BIN", "Fach", 100.0, true, level));

        Tool item = new Tool();
        item.setSku("E2E-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        item.setBarcode("4900000000099");
        item.setName("E2E-Artikel");
        item.setSpacePerUnit(0.1);
        item.setWarehouse(warehouse);
        item.getStockPositions().add(new StockPosition(null, item, null, null, null, bin,
                5, 0, StockStatus.AVAILABLE, null));
        item = itemRepository.save(item);

        Project project = new Project();
        project.setName("E2E-Auftrag");
        project.setCustomerName("E2E-Empfaenger");
        project.setDeliveryAddress("E2E-Strasse 1");
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
        pickOrderService.scan(pickOrder.getId(), lineId, new PickScanRequest("4900000000099", 2));
        pickOrderService.complete(pickOrder.getId());

        DeliveryNote note = packService.pack(pickOrder.getId());
        processor.processPendingEvents();

        DeliveryNote shipped = deliveryNoteRepository.findById(note.getId()).orElseThrow();
        assertEquals(ShippingStatus.SHIPPED, shipped.getShippingStatus());
        assertNotNull(shipped.getShippingShipmentId());
        assertNotNull(shipped.getTrackingNumber());
        ShippingOutboxEvent event = outboxRepository.findAll().stream()
                .filter(e -> note.getId().equals(e.getDeliveryNoteId()))
                .findFirst().orElseThrow();
        assertEquals(ShippingOutboxStatus.PROCESSED, event.getStatus());
        assertTrue(idempotencyRepository.findById(event.getIdempotencyKey()).isPresent());

        processor.processPendingEvents();
        assertTrue(idempotencyRepository.findById(event.getIdempotencyKey()).isPresent());
    }

    @Test
    void missingDeliveryNoteFailsInsteadOfSilentProcessed() {
        ShippingOutboxEvent event = new ShippingOutboxEvent();
        event.setIdempotencyKey("missing-note-" + java.util.UUID.randomUUID());
        event.setDeliveryNoteId(987654321L);
        event.setPayload("{\"deliveryNoteId\":987654321,\"reference\":\"REF\","
                + "\"recipient\":\"R\",\"address\":\"A\",\"packageCount\":1}");
        event.setStatus(ShippingOutboxStatus.PENDING);
        event.setCreatedAt(java.time.LocalDateTime.now());
        event.setAttempts(0);
        event = outboxRepository.save(event);
        processor.processSafely(event.getId());
        ShippingOutboxEvent stored = outboxRepository.findById(event.getId()).orElseThrow();
        // Fail-fast: never silently PROCESSED without a real delivery note.
        assertTrue(stored.getStatus() == ShippingOutboxStatus.PENDING
                || stored.getStatus() == ShippingOutboxStatus.FAILED);
        assertTrue(stored.getErrorMessage() != null
                && stored.getErrorMessage().contains("Lieferschein"));
    }
}
