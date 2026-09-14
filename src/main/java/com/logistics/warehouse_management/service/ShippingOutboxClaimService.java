package com.logistics.warehouse_management.service;

import com.logistics.warehouse_management.model.ShippingOutboxStatus;
import com.logistics.warehouse_management.repository.ShippingOutboxEventRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Own bean for outbox claims so every claim runs in its own
 * REQUIRES_NEW transaction and commits immediately — visible to other
 * nodes/workers right away. Avoids self-invocation proxy pitfalls:
 * the processor calls this bean instead of its own method.
 */
@Service
public class ShippingOutboxClaimService {

    private final ShippingOutboxEventRepository eventRepository;

    private ShippingOutboxClaimService self;

    public ShippingOutboxClaimService(ShippingOutboxEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public void setSelf(@Lazy ShippingOutboxClaimService self) {
        this.self = self;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int tryClaim(Long eventId, String workerId) {
        return eventRepository.claimPending(eventId,
                ShippingOutboxStatus.PENDING, ShippingOutboxStatus.PROCESSING, workerId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recoverOrphanedProcessingEvents(long leaseSeconds) {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(leaseSeconds);
        return eventRepository.resetOrphanedProcessing(
                ShippingOutboxStatus.PROCESSING, ShippingOutboxStatus.PENDING, cutoff,
                "Lease abgelaufen: Verarbeitung nach Absturz/Timeout erneut freigegeben");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean isAlreadyShipped(
            com.logistics.warehouse_management.repository.DeliveryNoteRepository deliveryNoteRepository,
            Long deliveryNoteId) {
        var note = deliveryNoteRepository.findById(deliveryNoteId)
                .orElseThrow(() -> new IllegalStateException("Lieferschein " + deliveryNoteId + " nicht gefunden"));
        return note.getShippingShipmentId() != null
                && com.logistics.warehouse_management.model.ShippingStatus.SHIPPED == note.getShippingStatus();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessed(
            com.logistics.warehouse_management.repository.DeliveryNoteRepository deliveryNoteRepository,
            Long eventId, String shipmentId, String trackingNumber, Long deliveryNoteId) {
        var event = eventRepository.findById(eventId).orElseThrow();
        var note = deliveryNoteRepository.findById(deliveryNoteId)
                .orElseThrow(() -> new IllegalStateException("Lieferschein " + deliveryNoteId + " nicht gefunden"));
            if (note.getShippingShipmentId() == null
                    || com.logistics.warehouse_management.model.ShippingStatus.SHIPPED != note.getShippingStatus()) {
                if (shipmentId != null) {
                    note.setShippingShipmentId(shipmentId);
                    note.setTrackingNumber(trackingNumber);
                    note.setShippingStatus(com.logistics.warehouse_management.model.ShippingStatus.SHIPPED);
                    deliveryNoteRepository.save(note);
                }
            }
        event.setStatus(ShippingOutboxStatus.PROCESSED);
        event.setProcessedAt(LocalDateTime.now());
        event.setProcessingStartedAt(null);
        event.setWorkerId(null);
        event.setErrorMessage(null);
        eventRepository.save(event);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long eventId, String errorMessage) {
        var event = eventRepository.findById(eventId).orElseThrow();
        int attempts = (event.getAttempts() == null ? 0 : event.getAttempts()) + 1;
        event.setAttempts(attempts);
        event.setErrorMessage(errorMessage);
        event.setProcessingStartedAt(null);
        event.setWorkerId(null);
        if (attempts >= 5) {
            event.setStatus(ShippingOutboxStatus.FAILED);
        } else {
            event.setStatus(ShippingOutboxStatus.PENDING);
            event.setNextAttemptAt(LocalDateTime.now().plusSeconds(Math.min(300, attempts * 30L)));
        }
        eventRepository.save(event);
    }

    /**
     * Ordered single-claim loop, hardened against duplicate candidate reads:
     * candidates are de-duplicated (LinkedHashSet keeps createdAt order) and
     * claimed one by one — each claim in its own REQUIRES_NEW transaction and
     * committed immediately. Portable on H2 and PostgreSQL (no native
     * SKIP LOCKED needed); concurrent workers still race only on the atomic
     * PENDING -&gt; PROCESSING update, so exactly one wins per event.
     *
     * @return ids this worker actually won, in claim order.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<Long> claimDueBatch(List<Long> candidateIds, String workerId) {
        List<Long> won = new ArrayList<>();
        java.util.LinkedHashSet<Long> unique = new java.util.LinkedHashSet<>(candidateIds);
        for (Long id : unique) {
            try {
                // Via self-proxy so REQUIRES_NEW really opens a new TX per
                // claim (direct this.tryClaim would bypass the proxy).
                if (self.tryClaim(id, workerId) == 1) {
                    won.add(id);
                }
            } catch (RuntimeException ex) {
                // Stale/deleted candidate — skip, do not abort the batch.
            }
        }
        return won;
    }
}
