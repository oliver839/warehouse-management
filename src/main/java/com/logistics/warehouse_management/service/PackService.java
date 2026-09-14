package com.logistics.warehouse_management.service;

import com.logistics.warehouse_management.model.DeliveryNote;
import com.logistics.warehouse_management.model.DeliveryNoteLine;
import com.logistics.warehouse_management.model.PackStatus;
import com.logistics.warehouse_management.model.PickLineStatus;
import com.logistics.warehouse_management.model.PickOrder;
import com.logistics.warehouse_management.model.PickOrderLine;
import com.logistics.warehouse_management.model.PickOrderStatus;
import com.logistics.warehouse_management.repository.DeliveryNoteRepository;
import com.logistics.warehouse_management.repository.PickOrderLineRepository;
import com.logistics.warehouse_management.repository.PickOrderRepository;
import com.logistics.warehouse_management.shipping.ShipmentRequest;
import com.logistics.warehouse_management.model.ShippingOutboxEvent;
import com.logistics.warehouse_management.model.ShippingOutboxStatus;
import com.logistics.warehouse_management.repository.ShippingOutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PackService {

    private final PickOrderRepository pickOrderRepository;
    private final PickOrderLineRepository lineRepository;
    private final DeliveryNoteRepository deliveryNoteRepository;
    private final InventoryService inventoryService;
    private final ProjectService projectService;
    private final ShippingOutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public PackService(PickOrderRepository pickOrderRepository,
                       PickOrderLineRepository lineRepository,
                       DeliveryNoteRepository deliveryNoteRepository,
                       InventoryService inventoryService,
                       ProjectService projectService,
                       ShippingOutboxEventRepository outboxRepository,
                       ObjectMapper objectMapper) {
        this.pickOrderRepository = pickOrderRepository;
        this.lineRepository = lineRepository;
        this.deliveryNoteRepository = deliveryNoteRepository;
        this.inventoryService = inventoryService;
        this.projectService = projectService;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DeliveryNote pack(Long pickOrderId) {
        PickOrder pickOrder = pickOrderRepository.findById(pickOrderId)
                .orElseThrow(() -> notFound("Pickauftrag nicht gefunden"));
        if (pickOrder.getStatus() != PickOrderStatus.COMPLETED) {
            throw badRequest("Nur vollständig gepickte Aufträge können gepackt werden.");
        }
        if (deliveryNoteRepository.existsByPickOrderId(pickOrderId)) {
            throw badRequest("Dieser Pickauftrag wurde bereits gepackt.");
        }
        List<PickOrderLine> pickLines = lineRepository.findByPickOrderIdOrderByBinLocationCodeAsc(pickOrderId);
        if (pickLines.isEmpty() || pickLines.stream().anyMatch(line -> line.getStatus() != PickLineStatus.PICKED
                && line.getStatus() != PickLineStatus.SHORTAGE_REPORTED)) {
            throw badRequest("Alle Pickpositionen müssen gepickt oder als Fehlmenge gemeldet sein.");
        }

        DeliveryNote note = new DeliveryNote();
        note.setDocumentNumber("DN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        note.setPickOrder(pickOrder);
        note.setRecipient(pickOrder.getProject().getCustomerName());
        note.setDeliveryAddress(pickOrder.getProject().getDeliveryAddress());
        note.setCreatedAt(LocalDateTime.now());
        note.setPackStatus(PackStatus.OPEN);
        note = deliveryNoteRepository.save(note);

        for (PickOrderLine pickLine : pickLines) {
            int quantity = pickLine.getPickedQuantity() == null ? 0 : pickLine.getPickedQuantity();
            if (quantity == 0) {
                continue;
            }
            DeliveryNoteLine noteLine = new DeliveryNoteLine();
            noteLine.setDeliveryNote(note);
            noteLine.setInventoryItem(pickLine.getInventoryItem());
            noteLine.setQuantity(quantity);
            noteLine.setShortageQuantity(pickLine.getShortageQuantity());
            noteLine.setShortageReason(pickLine.getShortageReason());
            noteLine.setBinLocation(pickLine.getBinLocation()); // null erlaubt: Bulk-Stock (ohne Lagerplatz)
            note.getLines().add(noteLine);
            inventoryService.consumeReservedStock(pickLine.getInventoryItem(), quantity,
                    "DELIVERY_NOTE", note.getId());
        }
        note.setPackStatus(PackStatus.PACKED);
        note = deliveryNoteRepository.save(note);
        ShippingOutboxEvent event = new ShippingOutboxEvent();
        event.setDeliveryNoteId(note.getId());
        event.setIdempotencyKey("delivery-note-" + note.getId());
        event.setPayload(payload(note));
        event.setStatus(ShippingOutboxStatus.PENDING);
        event.setCreatedAt(LocalDateTime.now());
        event.setAttempts(0);
        outboxRepository.save(event);
        projectService.markCompletedAfterPacking(pickOrder.getProject().getId());
        return note;
    }

    private String payload(DeliveryNote note) {
        try {
            return objectMapper.writeValueAsString(new com.logistics.warehouse_management.shipping.ShippingOutboxPayload(
                    note.getId(), note.getDocumentNumber(), note.getRecipient(), note.getDeliveryAddress(), 1));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Versandereignis konnte nicht erstellt werden", exception);
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}