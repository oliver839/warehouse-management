package com.logistics.warehouse_management.service;

import com.logistics.warehouse_management.model.InventoryTransactionReason;
import com.logistics.warehouse_management.model.StockPosition;
import com.logistics.warehouse_management.model.StockStatus;
import com.logistics.warehouse_management.repository.StockPositionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockStatusService {

    private final StockPositionRepository stockPositionRepository;
    private final InventoryService inventoryService;

    public StockStatusService(StockPositionRepository stockPositionRepository,
                              InventoryService inventoryService) {
        this.stockPositionRepository = stockPositionRepository;
        this.inventoryService = inventoryService;
    }

    @Transactional
    public StockPosition changeStatus(Long positionId, StockStatus status, String reference) {
        StockPosition position = stockPositionRepository.findById(positionId)
                .orElseThrow(() -> new IllegalArgumentException("StockPosition nicht gefunden"));
        if (status == StockStatus.AVAILABLE && position.getQuantity() < position.getReservedQuantity()) {
            throw new IllegalStateException("Reservierte Menge überschreitet den Bestand");
        }
        position.setStatus(status);
        StockPosition saved = stockPositionRepository.save(position);
        inventoryService.recordStatusChange(saved.getInventoryItem(), reference, positionId);
        return saved;
    }

    public StockPosition blockStock(Long id, String reference) { return changeStatus(id, StockStatus.BLOCKED, reference); }
    public StockPosition unblockStock(Long id, String reference) { return changeStatus(id, StockStatus.AVAILABLE, reference); }
    public StockPosition markDamaged(Long id, String reference) { return changeStatus(id, StockStatus.DAMAGED, reference); }
    public StockPosition sendToQualityCheck(Long id, String reference) { return changeStatus(id, StockStatus.QUALITY_CHECK, reference); }
    public StockPosition releaseFromQualityCheck(Long id, String reference) { return changeStatus(id, StockStatus.AVAILABLE, reference); }
}