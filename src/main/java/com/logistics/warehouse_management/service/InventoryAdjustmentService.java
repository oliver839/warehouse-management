package com.logistics.warehouse_management.service;

import com.logistics.warehouse_management.controller.InventoryAdjustmentRequest;
import com.logistics.warehouse_management.model.InventoryAdjustment;
import com.logistics.warehouse_management.model.InventoryAdjustmentStatus;
import com.logistics.warehouse_management.model.InventoryTransactionReason;
import com.logistics.warehouse_management.model.StockPosition;
import com.logistics.warehouse_management.repository.InventoryAdjustmentRepository;
import com.logistics.warehouse_management.repository.StockPositionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class InventoryAdjustmentService {

    private final InventoryAdjustmentRepository adjustmentRepository;
    private final StockPositionRepository positionRepository;
    private final InventoryService inventoryService;

    public InventoryAdjustmentService(InventoryAdjustmentRepository adjustmentRepository,
                                      StockPositionRepository positionRepository,
                                      InventoryService inventoryService) {
        this.adjustmentRepository = adjustmentRepository;
        this.positionRepository = positionRepository;
        this.inventoryService = inventoryService;
    }

    @Transactional
    public InventoryAdjustment request(InventoryAdjustmentRequest request) {
        StockPosition position = positionRepository.findById(request.stockPositionId())
                .orElseThrow(() -> notFound("StockPosition nicht gefunden"));
        if (request.requestedQuantity() == null || request.requestedQuantity() < 0) {
            throw badRequest("Die Zielmenge darf nicht negativ sein.");
        }
        InventoryAdjustment adjustment = new InventoryAdjustment();
        adjustment.setStockPosition(position);
        adjustment.setRequestedQuantity(request.requestedQuantity());
        adjustment.setOldQuantity(position.getQuantity());
        adjustment.setDelta(request.requestedQuantity() - position.getQuantity());
        adjustment.setReason(request.reason());
        adjustment.setNote(request.note());
        adjustment.setRequestedAt(LocalDateTime.now());
        adjustment.setRequestedBy(currentUser());
        return adjustmentRepository.save(adjustment);
    }

    public List<InventoryAdjustment> getAll() { return adjustmentRepository.findAll(); }

    @Transactional
    public InventoryAdjustment approve(Long id) {
        InventoryAdjustment adjustment = require(id);
        if (adjustment.getStatus() != InventoryAdjustmentStatus.REQUESTED) {
            throw badRequest("Diese Korrektur ist nicht mehr offen.");
        }
        // Single write path: correctPositionTo performs the only position write
        // plus the audit transaction. No additional position.setQuantity here.
        inventoryService.correctPositionTo(adjustment.getStockPosition(), adjustment.getRequestedQuantity(),
                InventoryTransactionReason.MANUAL_ADJUSTMENT, "INVENTORY_ADJUSTMENT", id);
        adjustment.setApprovedBy(currentUser());
        adjustment.setApprovedAt(LocalDateTime.now());
        adjustment.setStatus(InventoryAdjustmentStatus.APPLIED);
        return adjustmentRepository.save(adjustment);
    }

    @Transactional
    public InventoryAdjustment reject(Long id) {
        InventoryAdjustment adjustment = require(id);
        if (adjustment.getStatus() != InventoryAdjustmentStatus.REQUESTED) {
            throw badRequest("Diese Korrektur ist nicht mehr offen.");
        }
        adjustment.setApprovedBy(currentUser());
        adjustment.setApprovedAt(LocalDateTime.now());
        adjustment.setStatus(InventoryAdjustmentStatus.REJECTED);
        return adjustmentRepository.save(adjustment);
    }

    private InventoryAdjustment require(Long id) {
        return adjustmentRepository.findById(id).orElseThrow(() -> notFound("Korrektur nicht gefunden"));
    }

    private String currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "system" : authentication.getName();
    }

    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
}