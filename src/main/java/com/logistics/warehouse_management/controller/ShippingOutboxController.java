package com.logistics.warehouse_management.controller;

import com.logistics.warehouse_management.model.ShippingOutboxEvent;
import com.logistics.warehouse_management.repository.ShippingOutboxEventRepository;
import com.logistics.warehouse_management.service.ShippingOutboxProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/shipping-outbox")
public class ShippingOutboxController {

    private final ShippingOutboxProcessor processor;
    private final ShippingOutboxEventRepository eventRepository;

    public ShippingOutboxController(ShippingOutboxProcessor processor,
                                    ShippingOutboxEventRepository eventRepository) {
        this.processor = processor;
        this.eventRepository = eventRepository;
    }

    @PostMapping("/{id}/retry")
    public ShippingOutboxEvent retry(@PathVariable Long id) {
        return processor.retryFailedEvent(id);
    }

    @GetMapping("/by-delivery-note/{deliveryNoteId}")
    public ShippingOutboxEvent byDeliveryNote(@PathVariable Long deliveryNoteId) {
        List<ShippingOutboxEvent> events = eventRepository.findAll().stream()
                .filter(event -> deliveryNoteId.equals(event.getDeliveryNoteId()))
                .sorted(Comparator.comparing(ShippingOutboxEvent::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
        if (events.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Kein Versandereignis für diesen Lieferschein");
        }
        return events.get(0);
    }

    /**
     * Demo helper: triggers one scheduler pass on demand so a demo does not
     * have to wait for the 30s interval. Not a production pattern (single
     * node, no rate limit) — documented in README demo script.
     */
    @PostMapping("/process-due")
    public ProcessDueResult processDue() {
        int before = eventRepository.findAll().size();
        processor.processPendingEvents();
        return new ProcessDueResult(before);
    }

    public record ProcessDueResult(int eventsSeen) {
    }
}