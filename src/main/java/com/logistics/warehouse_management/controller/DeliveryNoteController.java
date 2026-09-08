package com.logistics.warehouse_management.controller;

import com.logistics.warehouse_management.model.DeliveryNote;
import com.logistics.warehouse_management.repository.DeliveryNoteRepository;
import com.logistics.warehouse_management.service.PackService;
import com.logistics.warehouse_management.service.PdfDocumentService;
import com.logistics.warehouse_management.shipping.ShippingProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/delivery-notes")
public class DeliveryNoteController {

    private final DeliveryNoteRepository deliveryNoteRepository;
    private final PackService packService;
    private final PdfDocumentService pdfService;
    private final ShippingProvider shippingProvider;

    public DeliveryNoteController(DeliveryNoteRepository deliveryNoteRepository,
                                  PackService packService, PdfDocumentService pdfService,
                                  ShippingProvider shippingProvider) {
        this.deliveryNoteRepository = deliveryNoteRepository;
        this.packService = packService;
        this.pdfService = pdfService;
        this.shippingProvider = shippingProvider;
    }

    @GetMapping
    public List<DeliveryNote> getAll() {
        return deliveryNoteRepository.findAll();
    }

    @GetMapping("/{id}")
    public DeliveryNote getOne(@PathVariable Long id) {
        return deliveryNoteRepository.findById(id).orElseThrow();
    }

    @PostMapping("/from-pick-order/{pickOrderId}/pack")
    public DeliveryNote pack(@PathVariable Long pickOrderId) {
        return packService.pack(pickOrderId);
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=lieferschein-" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfService.deliveryNote(id));
    }

    @GetMapping(value = "/{id}/label", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> label(@PathVariable Long id) {
        DeliveryNote note = deliveryNoteRepository.findById(id).orElseThrow();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=versandlabel-" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(shippingProvider.getLabel(note.getShippingShipmentId()));
    }

    @GetMapping("/{id}/shipping-status")
    public Object shippingStatus(@PathVariable Long id) {
        DeliveryNote note = deliveryNoteRepository.findById(id).orElseThrow();
        return shippingProvider.getStatus(note.getShippingShipmentId());
    }
}