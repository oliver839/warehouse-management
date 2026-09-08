package com.logistics.warehouse_management.service;

import com.logistics.warehouse_management.controller.GoodsReceiptLineRequest;
import com.logistics.warehouse_management.model.BinLocation;
import com.logistics.warehouse_management.model.GoodsReceipt;
import com.logistics.warehouse_management.model.GoodsReceiptLine;
import com.logistics.warehouse_management.model.GoodsReceiptStatus;
import com.logistics.warehouse_management.model.InventoryItem;
import com.logistics.warehouse_management.repository.GoodsReceiptLineRepository;
import com.logistics.warehouse_management.repository.GoodsReceiptRepository;
import com.logistics.warehouse_management.repository.InventoryItemRepository;
import com.logistics.warehouse_management.service.StorageLocationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class GoodsReceiptService {

    private final GoodsReceiptRepository receiptRepository;
    private final GoodsReceiptLineRepository lineRepository;
    private final InventoryItemRepository itemRepository;
    private final StorageLocationService locationService;
    private final InventoryService inventoryService;

    public GoodsReceiptService(GoodsReceiptRepository receiptRepository,
                               GoodsReceiptLineRepository lineRepository,
                               InventoryItemRepository itemRepository,
                               StorageLocationService locationService,
                               InventoryService inventoryService) {
        this.receiptRepository = receiptRepository;
        this.lineRepository = lineRepository;
        this.itemRepository = itemRepository;
        this.locationService = locationService;
        this.inventoryService = inventoryService;
    }

    public GoodsReceipt create(String receiptNumber, String supplierName, String receivedBy) {
        if (receiptNumber == null || receiptNumber.isBlank()) {
            throw badRequest("Eine Belegnummer ist erforderlich.");
        }
        GoodsReceipt receipt = new GoodsReceipt();
        receipt.setReceiptNumber(receiptNumber.trim());
        receipt.setSupplierName(supplierName);
        receipt.setReceivedBy(receivedBy == null || receivedBy.isBlank() ? "system" : receivedBy.trim());
        receipt.setReceivedAt(LocalDateTime.now());
        receipt.setStatus(GoodsReceiptStatus.DRAFT);
        return receiptRepository.save(receipt);
    }

    public List<GoodsReceiptLine> getLines(Long receiptId) {
        requireReceipt(receiptId);
        return lineRepository.findByGoodsReceiptId(receiptId);
    }

    public GoodsReceiptLine addLine(Long receiptId, GoodsReceiptLineRequest request) {
        GoodsReceipt receipt = requireReceipt(receiptId);
        if (receipt.getStatus() != GoodsReceiptStatus.DRAFT) {
            throw badRequest("Positionen können nur zu einem Entwurf hinzugefügt werden.");
        }
        if (request.inventoryItemId() == null || request.binLocationId() == null
                || request.expectedQuantity() == null || request.receivedQuantity() == null
                || request.expectedQuantity() < 0 || request.receivedQuantity() < 0) {
            throw badRequest("Artikel, Lagerplatz und gültige Mengen sind erforderlich.");
        }
        InventoryItem item = itemRepository.findById(request.inventoryItemId())
                .orElseThrow(() -> notFound("Item nicht gefunden"));
        BinLocation bin = locationService.getActiveBin(request.binLocationId());
        if (item.getWarehouse() != null && !item.getWarehouse().getId().equals(bin.getWarehouse().getId())) {
            throw badRequest("Der Lagerplatz gehört zu einem anderen Lager.");
        }
        GoodsReceiptLine line = new GoodsReceiptLine();
        line.setGoodsReceipt(receipt);
        line.setInventoryItem(item);
        line.setExpectedQuantity(request.expectedQuantity());
        line.setReceivedQuantity(request.receivedQuantity());
        line.setBinLocation(bin);
        return lineRepository.save(line);
    }

    @Transactional
    public GoodsReceipt confirm(Long receiptId) {
        GoodsReceipt receipt = requireReceipt(receiptId);
        if (receipt.getStatus() != GoodsReceiptStatus.DRAFT) {
            throw badRequest("Nur Entwürfe können bestätigt werden.");
        }
        List<GoodsReceiptLine> lines = lineRepository.findByGoodsReceiptId(receiptId);
        if (lines.isEmpty()) {
            throw badRequest("Ein Wareneingang benötigt mindestens eine Position.");
        }
        for (GoodsReceiptLine line : lines) {
            locationService.ensureCapacity(line.getBinLocation(), line.getInventoryItem(), line.getReceivedQuantity());
        }
        for (GoodsReceiptLine line : lines) {
            inventoryService.receive(line.getInventoryItem(), line.getReceivedQuantity(),
                    line.getBinLocation(), "GOODS_RECEIPT", receipt.getId());
        }
        receipt.setStatus(GoodsReceiptStatus.CONFIRMED);
        return receiptRepository.save(receipt);
    }

    private GoodsReceipt requireReceipt(Long id) {
        return receiptRepository.findById(id)
                .orElseThrow(() -> notFound("Wareneingang nicht gefunden"));
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}