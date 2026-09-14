package com.logistics.warehouse_management;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.warehouse_management.model.Aisle;
import com.logistics.warehouse_management.model.BinLocation;
import com.logistics.warehouse_management.model.DeliveryNote;
import com.logistics.warehouse_management.model.Project;
import com.logistics.warehouse_management.model.ProjectStatus;
import com.logistics.warehouse_management.model.Rack;
import com.logistics.warehouse_management.model.ShippingOutboxEvent;
import com.logistics.warehouse_management.model.ShippingOutboxStatus;
import com.logistics.warehouse_management.model.StorageLevel;
import com.logistics.warehouse_management.model.Warehouse;
import com.logistics.warehouse_management.model.WarehouseZone;
import com.logistics.warehouse_management.repository.DeliveryNoteRepository;
import com.logistics.warehouse_management.repository.ShippingOutboxEventRepository;
import com.logistics.warehouse_management.service.ShippingOutboxClaimService;
import com.logistics.warehouse_management.service.ShippingOutboxProcessor;
import com.logistics.warehouse_management.shipping.DemoShippingProvider;
import com.logistics.warehouse_management.shipping.ShipmentRequest;
import com.logistics.warehouse_management.shipping.ShipmentResponse;
import com.logistics.warehouse_management.shipping.ShippingProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "shipping.outbox.interval-ms=3600000",
        "shipping.outbox.lease-seconds=300"
})
class ShippingOutboxIntegrationTests {

    @Autowired
    private ShippingOutboxEventRepository eventRepository;

    @Autowired
    private DeliveryNoteRepository deliveryNoteRepository;

    @Autowired
    private ShippingOutboxProcessor processor;

    @Autowired
    private ShippingOutboxClaimService claimService;

    @Autowired
    private com.logistics.warehouse_management.repository.ShippingIdempotencyRecordRepository idempotencyRepository;

    @Autowired
    private com.logistics.warehouse_management.repository.WarehouseRepository warehouseRepository;

    @Autowired
    private com.logistics.warehouse_management.repository.ProjectRepository projectRepository;

    @Autowired
    private com.logistics.warehouse_management.repository.PickOrderRepository pickOrderRepository;

    @Autowired
    private com.logistics.warehouse_management.service.StorageLocationService locationService;

    @MockitoBean
    private ShippingProvider shippingProvider;

    @BeforeEach
    void stubProvider() {
        try {
            when(shippingProvider.createShipment(any(), any()))
                    .thenReturn(new ShipmentResponse("mock-ship", "MOCK-TRACK", "CREATED"));
        } catch (Exception ignored) {
        }
    }

    @Test
    void orphanedProcessingEventIsRecoveredAfterLeaseExpiry() {
        ShippingOutboxEvent event = newEvent("orphan-" + UUID.randomUUID());
        event.setStatus(ShippingOutboxStatus.PROCESSING);
        event.setProcessingStartedAt(LocalDateTime.now().minusMinutes(30));
        event.setWorkerId("dead-worker");
        event = eventRepository.save(event);

        int reset = processor.recoverOrphanedProcessingEvents();

        assertTrue(reset >= 1);
        ShippingOutboxEvent recovered = eventRepository.findById(event.getId()).orElseThrow();
        assertEquals(ShippingOutboxStatus.PENDING, recovered.getStatus());
    }
    @Test
    void freshProcessingEventKeepsLease() {
        ShippingOutboxEvent event = newEvent("fresh-" + UUID.randomUUID());
        event.setStatus(ShippingOutboxStatus.PROCESSING);
        event.setProcessingStartedAt(LocalDateTime.now());
        event.setWorkerId("live-worker");
        event = eventRepository.save(event);

        processor.recoverOrphanedProcessingEvents();

        ShippingOutboxEvent stored = eventRepository.findById(event.getId()).orElseThrow();
        assertEquals(ShippingOutboxStatus.PROCESSING, stored.getStatus());
    }

    @Test
    void retryAfterFailureEventuallyDeadLettersAndAdminCanReset() {
        ShippingOutboxEvent event = newEvent("retry-" + UUID.randomUUID());
        event.setPayload("not-json");
        eventRepository.save(event);
        for (int i = 0; i < 5; i++) {
            ShippingOutboxEvent loaded = eventRepository.findById(event.getId()).orElseThrow();
            processor.processSafely(loaded);
        }
        ShippingOutboxEvent failed = eventRepository.findById(event.getId()).orElseThrow();
        assertEquals(ShippingOutboxStatus.FAILED, failed.getStatus());
        assertEquals(5, failed.getAttempts());

        ShippingOutboxEvent retried = processor.retryFailedEvent(failed.getId());
        assertEquals(ShippingOutboxStatus.PENDING, retried.getStatus());
    }

    @Test
    void providerIdempotencyKeyReturnsSameShipment() {
        DemoShippingProvider provider = new DemoShippingProvider();
        String key = "idem-" + UUID.randomUUID();
        ShipmentRequest request = new ShipmentRequest("ref", "rec", "addr", 1);
        ShipmentResponse first = provider.createShipment(request, key);
        ShipmentResponse second = provider.createShipment(request, key);
        assertEquals(first.shipmentId(), second.shipmentId());
        assertEquals(1, provider.getProviderCalls());
    }

    @Test
    void parallelClaimingProcessesEventExactlyOnce() throws Exception {
        ShippingOutboxEvent event = newEvent("parallel-" + UUID.randomUUID());
        Long id = event.getId();
        int workers = 4;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(workers);
        java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(workers);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.List<java.util.concurrent.Future<Integer>> futures = new java.util.ArrayList<>();
        try {
            for (int i = 0; i < workers; i++) {
                final String wid = "worker-" + i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await(10, java.util.concurrent.TimeUnit.SECONDS);
                    return claimService.tryClaim(id, wid);
                }));
            }
            // Wait until all workers are ready, then release them simultaneously.
            assertTrue(ready.await(10, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();
            int total = 0;
            for (java.util.concurrent.Future<Integer> f : futures) {
                total += f.get(30, java.util.concurrent.TimeUnit.SECONDS);
            }
            // Exactly one worker wins the atomic PENDING -> PROCESSING claim,
            // each claim committed in its own REQUIRES_NEW transaction.
            assertEquals(1, total);
        } finally {
            pool.shutdownNow();
        }
        ShippingOutboxEvent stored = eventRepository.findById(id).orElseThrow();
        assertEquals(ShippingOutboxStatus.PROCESSING, stored.getStatus());
    }

    private ShippingOutboxEvent newEvent(String key) {
        DeliveryNote note = newDeliveryNote();
        ShippingOutboxEvent event = new ShippingOutboxEvent();
        event.setIdempotencyKey(key);
        event.setDeliveryNoteId(note.getId());
        event.setPayload("{\"deliveryNoteId\":" + note.getId() + ",\"reference\":\""
                + note.getDocumentNumber() + "\",\"recipient\":\"R\",\"address\":\"A\",\"packageCount\":1}");
        event.setStatus(ShippingOutboxStatus.PENDING);
        event.setCreatedAt(LocalDateTime.now());
        event.setAttempts(0);
        return eventRepository.save(event);
    }

    private DeliveryNote newDeliveryNote() {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Warehouse warehouse = warehouseRepository.save(
                new Warehouse(null, "Syn-Lager", "Testort", 100.0, null));
        WarehouseZone zone = locationService.saveZone(new WarehouseZone(null, "SYN-Z-" + suffix, "Z", warehouse));
        Aisle aisle = locationService.saveAisle(new Aisle(null, "SYN-A-" + suffix, "A", zone));
        Rack rack = locationService.saveRack(new Rack(null, "SYN-R-" + suffix, "R", aisle));
        StorageLevel level = locationService.saveLevel(new StorageLevel(null, "SYN-L-" + suffix, "L", rack));
        locationService.saveBin(new BinLocation(null, "SYN-BIN-" + suffix, "Fach", 100.0, true, level));
        Project project = new Project();
        project.setName("Syn-Auftrag");
        project.setCustomerName("R");
        project.setDeliveryAddress("A");
        project.setStatus(ProjectStatus.PENDING);
        project = projectRepository.save(project);
        com.logistics.warehouse_management.model.PickOrder pickOrder =
                new com.logistics.warehouse_management.model.PickOrder();
        pickOrder.setProject(project);
        pickOrder.setStatus(com.logistics.warehouse_management.model.PickOrderStatus.CREATED);
        pickOrder.setCreatedAt(LocalDateTime.now());
        pickOrder = pickOrderRepository.save(pickOrder);
        DeliveryNote note = new DeliveryNote();
        note.setDocumentNumber("DN-SYN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        note.setPickOrder(pickOrder);
        note.setRecipient("R");
        note.setDeliveryAddress("A");
        note.setCreatedAt(LocalDateTime.now());
        return deliveryNoteRepository.save(note);
    }

    @Test
    void batchClaimDistributesEventsDisjointly() throws Exception {
        int total = 20;
        java.util.List<Long> ids = new java.util.ArrayList<>();
        for (int i = 0; i < total; i++) {
            ids.add(newEvent("batch-" + UUID.randomUUID()).getId());
        }
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Future<java.util.List<Long>> a =
                    pool.submit(() -> claimService.claimDueBatch(ids, "batch-worker-a"));
            java.util.concurrent.Future<java.util.List<Long>> b =
                    pool.submit(() -> claimService.claimDueBatch(ids, "batch-worker-b"));
            java.util.List<Long> wonA = a.get(30, java.util.concurrent.TimeUnit.SECONDS);
            java.util.List<Long> wonB = b.get(30, java.util.concurrent.TimeUnit.SECONDS);
            java.util.Set<Long> union = new java.util.HashSet<>(wonA);
            union.addAll(wonB);
            assertEquals(total, union.size());
            assertTrue(wonA.stream().noneMatch(wonB::contains));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void providerCallRunsOutsideTransaction() {
        ShippingOutboxEvent event = newEvent("notx-" + UUID.randomUUID());
        Long id = event.getId();
        claimService.tryClaim(id, "notx-worker");
        java.util.concurrent.atomic.AtomicBoolean inTx = new java.util.concurrent.atomic.AtomicBoolean(true);
        ShippingProvider probe = org.mockito.Mockito.mock(ShippingProvider.class);
        try {
            org.mockito.Mockito.when(probe.createShipment(org.mockito.ArgumentMatchers.any(),
                            org.mockito.ArgumentMatchers.eq(event.getIdempotencyKey())))
                    .thenAnswer(inv -> {
                        inTx.set(org.springframework.transaction.support
                                .TransactionSynchronizationManager.isActualTransactionActive());
                        return new ShipmentResponse("ship-notx", "TRACK-NOTX", "CREATED");
                    });
        } catch (Exception ignored) {
        }
        var isolated = new ShippingOutboxProcessor(eventRepository, deliveryNoteRepository,
                idempotencyRepository, probe, new ObjectMapper(), claimService, 300);
        isolated.processSafely(id);
        assertFalse(inTx.get(), "Carrier call must run outside any DB transaction");
    }
    @Test
    void idempotencySurvivesRestartWithoutSecondCarrierCall() {
        String key = "restart-" + UUID.randomUUID();
        ShippingOutboxEvent event = newEvent("restart-first-" + UUID.randomUUID());
        event.setIdempotencyKey(key);
        event = eventRepository.save(event);
        Long id = event.getId();
        claimService.tryClaim(id, "restart-worker");
        DemoShippingProvider provider1 = new DemoShippingProvider();
        var run1 = new ShippingOutboxProcessor(eventRepository, deliveryNoteRepository,
                idempotencyRepository, provider1, new ObjectMapper(), claimService, 300);
        run1.processSafely(id);
        assertEquals(1, provider1.getProviderCalls());
        // Simulate restart: brand-new provider with empty in-memory map, same DB.
        // No new outbox row (idempotencyKey is unique per event); instead the
        // carrier step is re-executed for the same event, e.g. after a crash
        // before finish-TX. Persistent record must prevent a second call.
        ShippingOutboxEvent stored = eventRepository.findById(id).orElseThrow();
        stored.setStatus(ShippingOutboxStatus.PROCESSING);
        stored = eventRepository.save(stored);
        DemoShippingProvider provider2 = new DemoShippingProvider();
        var run2 = new ShippingOutboxProcessor(eventRepository, deliveryNoteRepository,
                idempotencyRepository, provider2, new ObjectMapper(), claimService, 300);
        run2.processSafely(stored.getId());
        assertEquals(0, provider2.getProviderCalls());
        assertTrue(idempotencyRepository.findById(key).isPresent());
    }

    @Test
    void crashAfterProviderCallReconcilesWithoutSecondCall() throws Exception {
        String key = "crash-" + UUID.randomUUID();
        ShippingOutboxEvent event = newEvent(key);
        Long id = event.getId();
        claimService.tryClaim(id, "crash-worker");
        ShippingProvider crashing = org.mockito.Mockito.mock(ShippingProvider.class);
        try {
            org.mockito.Mockito.when(crashing.createShipment(org.mockito.ArgumentMatchers.any(),
                            org.mockito.ArgumentMatchers.eq(key)))
                    .thenReturn(new ShipmentResponse("ship-crash", "TRACK-CRASH", "CREATED"));
        } catch (Exception ignored) {
        }
        var run = new ShippingOutboxProcessor(eventRepository, deliveryNoteRepository,
                idempotencyRepository, crashing, new ObjectMapper(), claimService, 300);
        ShippingOutboxEvent snapshot = eventRepository.findById(id).orElseThrow();
        var parsed = new ObjectMapper().readValue(snapshot.getPayload(),
                com.logistics.warehouse_management.shipping.ShippingOutboxPayload.class);
        run.createShipmentIdempotent(snapshot, parsed);
        DemoShippingProvider fresh = new DemoShippingProvider();
        var recovery = new ShippingOutboxProcessor(eventRepository, deliveryNoteRepository,
                idempotencyRepository, fresh, new ObjectMapper(), claimService, 300);
        recovery.processSafely(id);
        assertEquals(0, fresh.getProviderCalls());
        ShippingOutboxEvent stored = eventRepository.findById(id).orElseThrow();
        assertEquals(ShippingOutboxStatus.PROCESSED, stored.getStatus());
    }
}


