package com.logistics.warehouse_management.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.warehouse_management.model.ShippingIdempotencyRecord;
import com.logistics.warehouse_management.model.ShippingOutboxEvent;
import com.logistics.warehouse_management.model.ShippingOutboxStatus;
import com.logistics.warehouse_management.repository.DeliveryNoteRepository;
import com.logistics.warehouse_management.repository.ShippingIdempotencyRecordRepository;
import com.logistics.warehouse_management.repository.ShippingOutboxEventRepository;
import com.logistics.warehouse_management.shipping.ShipmentRequest;
import com.logistics.warehouse_management.shipping.ShipmentResponse;
import com.logistics.warehouse_management.shipping.ShippingOutboxPayload;
import com.logistics.warehouse_management.shipping.ShippingProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class ShippingOutboxProcessor {

    private final ShippingOutboxEventRepository eventRepository;
    private final DeliveryNoteRepository deliveryNoteRepository;
    private final ShippingIdempotencyRecordRepository idempotencyRepository;
    private final ShippingProvider shippingProvider;
    private final ObjectMapper objectMapper;
    private final ShippingOutboxClaimService claimService;

    private final String workerId = "wms-outbox-" + UUID.randomUUID().toString().substring(0, 8);

    private final long leaseSeconds;

    public ShippingOutboxProcessor(ShippingOutboxEventRepository eventRepository,
                                   DeliveryNoteRepository deliveryNoteRepository,
                                   ShippingIdempotencyRecordRepository idempotencyRepository,
                                   ShippingProvider shippingProvider,
                                   ObjectMapper objectMapper,
                                   ShippingOutboxClaimService claimService,
                                   @Value("${shipping.outbox.lease-seconds:300}") long leaseSeconds) {
        this.eventRepository = eventRepository;
        this.deliveryNoteRepository = deliveryNoteRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.shippingProvider = shippingProvider;
        this.objectMapper = objectMapper;
        this.claimService = claimService;
        this.leaseSeconds = leaseSeconds;
    }

    @Scheduled(fixedDelayString = "${shipping.outbox.interval-ms:30000}")
    public void processPendingEvents() {
        recoverOrphanedProcessingEvents();
        // Hardened candidate handling: both due-lists are merged de-duplicated
        // (keeps createdAt order) and claimed via the ordered single-claim loop.
        List<Long> candidateIds = Stream.concat(
                eventRepository.findTop20ByStatusAndNextAttemptAtIsNullOrderByCreatedAtAsc(ShippingOutboxStatus.PENDING).stream(),
                eventRepository.findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(ShippingOutboxStatus.PENDING, LocalDateTime.now()).stream())
            .map(ShippingOutboxEvent::getId)
            .collect(Collectors.toList());
        for (Long id : claimService.claimDueBatch(candidateIds, workerId)) {
            processSafely(id);
        }
    }

    /**
     * Recovery for crashed workers: PROCESSING events whose lease expired
     * (processingStartedAt older than leaseSeconds) are reset to PENDING so a
     * live worker can retry them. Provider-side duplication is prevented only
     * if the carrier honors the Idempotency-Key (verified per provider).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recoverOrphanedProcessingEvents() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(leaseSeconds);
        return claimService.recoverOrphanedProcessingEvents(leaseSeconds);
    }

    @Transactional
    public void processSafely(ShippingOutboxEvent event) {
        processSafely(event.getId());
    }

    /**
     * IO decoupled from DB transactions: this orchestrator runs WITHOUT a
     * transaction. Payload parsing and the carrier call happen outside any TX;
     * only short REQUIRES_NEW steps touch the DB (pre-check, finish, fail).
     * The pool connection is never held during external IO.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void processSafely(Long eventId) {
        ShippingOutboxPayload payload;
        ShippingOutboxEvent snapshot;
        try {
            snapshot = eventRepository.findById(eventId).orElseThrow();
            payload = objectMapper.readValue(snapshot.getPayload(), ShippingOutboxPayload.class);
        } catch (Exception exception) {
            claimService.markFailed(eventId, exception.getMessage());
            return;
        }
        // Short read-TX (own bean -> real REQUIRES_NEW proxy): already shipped?
        // Fail-fast: missing delivery note must fail the event, never silently
        // mark PROCESSED (compliance risk).
        try {
            if (claimService.isAlreadyShipped(deliveryNoteRepository, payload.deliveryNoteId())) {
                claimService.markProcessed(deliveryNoteRepository, eventId, null, null, payload.deliveryNoteId());
                return;
            }
        } catch (IllegalStateException missingNote) {
            claimService.markFailed(eventId, missingNote.getMessage());
            return;
        }
        // Carrier call WITHOUT transaction (NOT_SUPPORTED suspends any caller TX).
        ShipmentResponse response;
        try {
            response = createShipmentIdempotent(snapshot, payload);
        } catch (Exception exception) {
            claimService.markFailed(eventId, exception.getMessage());
            return;
        }
        // Short write-TX with post-check against concurrent workers.
        claimService.markProcessed(deliveryNoteRepository, eventId,
                response.shipmentId(), response.trackingNumber(), payload.deliveryNoteId());
    }

    /**
     * Persistent idempotency: same key returns the stored shipment without a
     * second carrier call — survives JVM restarts. On a race (two workers
     * insert the same key), the loser catches DataIntegrityViolation and
     * re-reads the winner's record.
     */
    public ShipmentResponse createShipmentIdempotent(ShippingOutboxEvent event, ShippingOutboxPayload payload) {
        String key = event.getIdempotencyKey();
        if (key != null) {
            var existing = idempotencyRepository.findById(key);
            if (existing.isPresent()) {
                return new ShipmentResponse(existing.get().getShipmentId(),
                        existing.get().getTrackingNumber(), "CREATED");
            }
            ShipmentResponse response = shippingProvider.createShipment(new ShipmentRequest(
                    payload.reference(), payload.recipient(), payload.address(), payload.packageCount()), key);
            try {
                idempotencyRepository.save(new ShippingIdempotencyRecord(key,
                        response.shipmentId(), response.trackingNumber(), LocalDateTime.now()));
            } catch (DataIntegrityViolationException race) {
                var winner = idempotencyRepository.findById(key).orElseThrow();
                return new ShipmentResponse(winner.getShipmentId(), winner.getTrackingNumber(), "CREATED");
            }
            return response;
        }
        return shippingProvider.createShipment(new ShipmentRequest(
                payload.reference(), payload.recipient(), payload.address(), payload.packageCount()), null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ShippingOutboxEvent retryFailedEvent(Long id) {
        if (eventRepository.retryFailed(id, ShippingOutboxStatus.FAILED, ShippingOutboxStatus.PENDING) != 1) {
            throw new IllegalArgumentException("Outbox-Event ist nicht fehlgeschlagen oder nicht vorhanden");
        }
        return eventRepository.findById(id).orElseThrow();
    }
}