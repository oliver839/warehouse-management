package com.logistics.warehouse_management.controller;

import com.logistics.warehouse_management.model.GoodsReceipt;
import com.logistics.warehouse_management.model.GoodsReceiptLine;
import com.logistics.warehouse_management.repository.GoodsReceiptRepository;
import com.logistics.warehouse_management.service.GoodsReceiptService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/goods-receipts")
public class GoodsReceiptController {

    private final GoodsReceiptRepository receiptRepository;
    private final GoodsReceiptService service;

    public GoodsReceiptController(GoodsReceiptRepository receiptRepository,
                                  GoodsReceiptService service) {
        this.receiptRepository = receiptRepository;
        this.service = service;
    }

    @GetMapping
    public List<GoodsReceipt> getReceipts() {
        return receiptRepository.findAll();
    }

    @PostMapping
    public GoodsReceipt createReceipt(@RequestBody GoodsReceiptRequest request) {
        return service.create(request.receiptNumber(), request.supplierName(), request.receivedBy());
    }

    @GetMapping("/{id}/lines")
    public List<GoodsReceiptLine> getLines(@PathVariable Long id) {
        return service.getLines(id);
    }

    @PostMapping("/{id}/lines")
    public GoodsReceiptLine addLine(@PathVariable Long id,
                                    @RequestBody GoodsReceiptLineRequest request) {
        return service.addLine(id, request);
    }

    @PostMapping("/{id}/confirm")
    public GoodsReceipt confirm(@PathVariable Long id) {
        return service.confirm(id);
    }
}