package com.logistics.warehouse_management.controller;

import com.logistics.warehouse_management.model.PickOrder;
import com.logistics.warehouse_management.model.PickOrderLine;
import com.logistics.warehouse_management.model.PickScan;
import com.logistics.warehouse_management.service.PickOrderService;
import com.logistics.warehouse_management.service.PdfDocumentService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/pick-orders")
public class PickOrderController {

    private final PickOrderService service;
    private final PdfDocumentService pdfService;

    public PickOrderController(PickOrderService service, PdfDocumentService pdfService) {
        this.service = service;
        this.pdfService = pdfService;
    }

    @GetMapping
    public List<PickOrder> getAll() {
        return service.getAll();
    }

    @PostMapping("/from-project/{projectId}")
    public PickOrder createFromProject(@PathVariable Long projectId) {
        return service.create(projectId);
    }

    @GetMapping("/{id}/lines")
    public List<PickOrderLine> getLines(@PathVariable Long id) {
        return service.getLines(id);
    }

    @PostMapping("/{id}/start")
    public PickOrder start(@PathVariable Long id) {
        return service.start(id);
    }

    @PostMapping("/{id}/lines/{lineId}/scan")
    public PickOrder scan(@PathVariable Long id,
                          @PathVariable Long lineId,
                          @RequestBody PickScanRequest request) {
        return service.scan(id, lineId, request);
    }

    @GetMapping("/{id}/lines/{lineId}/scans")
    public List<PickScan> getScans(@PathVariable Long id, @PathVariable Long lineId) {
        return service.getScans(id, lineId);
    }

    @PostMapping("/{id}/complete")
    public PickOrder complete(@PathVariable Long id) {
        return service.complete(id);
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=pickliste-" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfService.pickList(id));
    }
}