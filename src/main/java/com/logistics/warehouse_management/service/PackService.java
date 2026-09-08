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
import com.logistics.warehouse_management.shipping.ShipmentResponse;
import com.logistics.warehouse_management.shipping.ShippingProvider;
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
    private final ShippingProvider shippingProvider;

    public PackService(PickOrderRepository pickOrderRepository,
                       PickOrderLineRepository lineRepository,
                       DeliveryNoteRepository deliveryNoteRepository,
                       InventoryService inventoryService,
                       ProjectService projectService,
                       ShippingProvider shippingProvider) {
        this.pickOrderRepository = pickOrderRepository;
        this.lineRepository = lineRepository;
        this.deliveryNoteRepository = deliveryNoteRepository;
        this.inventoryService = inventoryService;
        this.projectService = projectService;
        this.shippingProvider = shippingProvider;
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
        if (pickLines.isEmpty() || pickLines.stream().anyMatch(line -> line.getStatus() != PickLineStatus.PICKED)) {
            throw badRequest("Alle Pickpositionen müssen vollständig gepickt sein.");
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
            int quantity = pickLine.getRequiredQuantity();
            DeliveryNoteLine noteLine = new DeliveryNoteLine();
            noteLine.setDeliveryNote(note);
            noteLine.setInventoryItem(pickLine.getInventoryItem());
            noteLine.setQuantity(quantity);
            noteLine.setBinLocation(pickLine.getBinLocation());
            note.getLines().add(noteLine);
            inventoryService.consumeReservedStock(pickLine.getInventoryItem(), quantity,
                    "DELIVERY_NOTE", note.getId());
        }
        note.setPackStatus(PackStatus.PACKED);
        ShipmentResponse shipment = shippingProvider.createShipment(new ShipmentRequest(
            note.getDocumentNumber(), note.getRecipient(), note.getDeliveryAddress(), 1));
        note.setShippingShipmentId(shipment.shipmentId());
        note.setTrackingNumber(shipment.trackingNumber());
        note.setShippingStatus(com.logistics.warehouse_management.model.ShippingStatus.SHIPPED);
        note = deliveryNoteRepository.save(note);
        projectService.markCompletedAfterPacking(pickOrder.getProject().getId());
        return note;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}