package com.logistics.warehouse_management.service;

import com.logistics.warehouse_management.model.InventoryItem;
import com.logistics.warehouse_management.model.BinLocation;
import com.logistics.warehouse_management.model.InventoryTransaction;
import com.logistics.warehouse_management.model.InventoryTransactionReason;
import com.logistics.warehouse_management.model.StockPosition;
import com.logistics.warehouse_management.model.StockStatus;
import com.logistics.warehouse_management.repository.InventoryItemRepository;
import com.logistics.warehouse_management.repository.InventoryTransactionRepository;
import com.logistics.warehouse_management.repository.StockPositionRepository;
import jakarta.persistence.OptimisticLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class
InventoryService {

    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final StockPositionRepository stockPositionRepository;

    public InventoryService(InventoryItemRepository inventoryItemRepository,
                            InventoryTransactionRepository transactionRepository,
                            StockPositionRepository stockPositionRepository) {
        this.inventoryItemRepository = inventoryItemRepository;
        this.transactionRepository = transactionRepository;
        this.stockPositionRepository = stockPositionRepository;
    }

    @Transactional
    public void reserve(InventoryItem item, int quantity) {
        try {
            if (quantity <= 0) throw new IllegalStateException("Nicht genug Bestand verfügbar");
            List<StockPosition> locked = stockPositionRepository.findByInventoryItemIdForUpdate(item.getId());
            // Kommissionierfaehig = Status AVAILABLE und (kein Bin = Bulk-Bestand
            // oder aktiver Bin). Null-Bin muss erlaubt sein, sonst blocken
            // Demo-/Altdaten (DataInitializer, Item-Anlage ohne Bin) jede Genehmigung.
            List<StockPosition> pickable = locked.stream()
                    .filter(position -> position.getStatus() == StockStatus.AVAILABLE)
                    .filter(position -> position.getBinLocation() == null || position.getBinLocation().isActive())
                    .toList();
            int available = pickable.stream().mapToInt(p ->
                    Math.max(0, (p.getQuantity() == null ? 0 : p.getQuantity())
                            - (p.getReservedQuantity() == null ? 0 : p.getReservedQuantity()))).sum();
            if (available <= 0) {
                throw new IllegalStateException("Kein kommissionierfähiger Bestand (Status AVAILABLE) für " + item.getName());
            }
            if (quantity > available) throw new IllegalStateException("Nicht genug Bestand verfügbar");
            int remaining = quantity;
            for (StockPosition position : pickable) {
                int free = Math.max(0, (position.getQuantity() == null ? 0 : position.getQuantity())
                        - (position.getReservedQuantity() == null ? 0 : position.getReservedQuantity()));
                int reserved = Math.min(remaining, free);
                position.setReservedQuantity((position.getReservedQuantity() == null ? 0 : position.getReservedQuantity()) + reserved);
                stockPositionRepository.save(position);
                remaining -= reserved;
                if (remaining == 0) break;
            }
            if (remaining > 0) throw new IllegalStateException("Nicht genug Bestand verfügbar");
            record(item, 0, totalStock(item), totalStock(item), InventoryTransactionReason.RESERVATION, null, null);
        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException | PessimisticLockingFailureException exception) {
            throw new IllegalStateException("Bestand wurde parallel geändert. Bitte erneut versuchen.", exception);
        }
    }

    @Transactional
    public void releaseReservation(InventoryItem item, int quantity) {
        List<StockPosition> locked = stockPositionRepository.findByInventoryItemIdForUpdate(item.getId());
        int remaining = quantity;
        for (StockPosition position : locked) {
            int reserved = position.getReservedQuantity() == null ? 0 : position.getReservedQuantity();
            int released = Math.min(remaining, reserved);
            position.setReservedQuantity(reserved - released);
            stockPositionRepository.save(position);
            remaining -= released;
            if (remaining == 0) break;
        }
        record(item, 0, totalStock(item), totalStock(item), InventoryTransactionReason.RELEASE, null, null);
    }

    @Transactional
    public void consumeReservedStock(InventoryItem item, int quantity) {
        consumeReservedStock(item, quantity, null, null);
    }

    @Transactional
    public void consumeReservedStock(InventoryItem item, int quantity,
                                     String referenceType, Long referenceId) {
        try {
            List<StockPosition> locked = stockPositionRepository.findByInventoryItemIdForUpdate(item.getId());
            int total = locked.stream().mapToInt(p -> p.getQuantity() == null ? 0 : p.getQuantity()).sum();
            int reserved = locked.stream().mapToInt(p -> p.getReservedQuantity() == null ? 0 : p.getReservedQuantity()).sum();
            if (quantity <= 0 || quantity > reserved || quantity > total) {
                throw new IllegalStateException("Reservierter Bestand ist nicht verfügbar");
            }
            int remaining = quantity;
            for (StockPosition position : locked) {
                int posReserved = position.getReservedQuantity() == null ? 0 : position.getReservedQuantity();
                int posQty = position.getQuantity() == null ? 0 : position.getQuantity();
                int consumed = Math.min(remaining, Math.min(posReserved, posQty));
                position.setQuantity(posQty - consumed);
                position.setReservedQuantity(posReserved - consumed);
                stockPositionRepository.save(position);
                remaining -= consumed;
                if (remaining == 0) break;
            }
            if (remaining > 0) throw new IllegalStateException("Reservierter Bestand ist nicht verfügbar");
            record(item, -quantity, total, total - quantity, InventoryTransactionReason.SHIPMENT, referenceType, referenceId);
        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException | PessimisticLockingFailureException exception) {
            throw new IllegalStateException("Bestand wurde parallel geändert. Bitte erneut versuchen.", exception);
        }
    }

    @Transactional
    public void receive(InventoryItem item, int quantity, BinLocation bin,
                        String referenceType, Long referenceId) {
        if (quantity <= 0) {
            throw new IllegalStateException("Die Liefermenge muss positiv sein");
        }
        int stock = totalStock(item);
        StockPosition position = stockPositionRepository.findByInventoryItemIdAndBinLocationId(item.getId(), bin.getId()).orElseGet(() -> {
            StockPosition created = new StockPosition();
            created.setInventoryItem(item);
            created.setBinLocation(bin);
            created.setStatus(StockStatus.AVAILABLE);
            return created;
        });
        position.setQuantity((position.getQuantity() == null ? 0 : position.getQuantity()) + quantity);
        stockPositionRepository.save(position);
        register(item, position);
        record(item, quantity, stock, stock + quantity, InventoryTransactionReason.GOODS_RECEIPT, referenceType, referenceId);
    }

    /**
     * Single write path for position-level corrections.
     * Adjusts exactly one position to the requested physical quantity and releases
     * an optional reservation share. Callers must not write the position again
     * after invoking this method (no double-write).
     */
    @Transactional
    public void correctPositionTo(StockPosition attachedPosition, int requestedQuantity,
                                  InventoryTransactionReason reason, String referenceType, Long referenceId) {
        if (requestedQuantity < 0) {
            throw new IllegalArgumentException("Bestand darf nicht negativ sein");
        }
        StockPosition position = stockPositionRepository.findByIdForUpdate(attachedPosition.getId())
                .orElseThrow(() -> new IllegalStateException("StockPosition nicht gefunden"));
        int previous = position.getQuantity() == null ? 0 : position.getQuantity();
        if (previous == requestedQuantity) {
            return;
        }
        int currentReserved = position.getReservedQuantity() == null ? 0 : position.getReservedQuantity();
        position.setQuantity(requestedQuantity);
        position.setReservedQuantity(Math.min(currentReserved, requestedQuantity));
        stockPositionRepository.save(position);
        record(position.getInventoryItem(), requestedQuantity - previous, previous, requestedQuantity,
                reason, referenceType, referenceId);
    }

    @Transactional
    public void adjustStock(InventoryItem item, int newQuantity,
                            InventoryTransactionReason reason, String referenceType, Long referenceId) {
        if (newQuantity < 0) {
            throw new IllegalArgumentException("Bestand darf nicht negativ sein");
        }
        int previous = totalStock(item);
        if (previous == newQuantity) {
            return;
        }
        int delta = newQuantity - previous;
        StockPosition position = positions(item).stream().findFirst().orElseGet(() -> createLegacyPosition(item));
        position.setQuantity(Math.max(0, position.getQuantity() + delta));
        stockPositionRepository.save(position);
        register(item, position);
        record(item, delta, previous, newQuantity, reason, referenceType, referenceId);
    }

    @Transactional
    public void reconcileShortage(InventoryItem item, BinLocation bin, int physicalQuantity,
                                  int reservationToRelease, String referenceType, Long referenceId) {
        if (physicalQuantity < 0 || reservationToRelease < 0) {
            throw new IllegalArgumentException("Fehlmengenwerte dürfen nicht negativ sein");
        }
        StockPosition position = stockPositionRepository
                .findByInventoryItemIdAndBinLocationId(item.getId(), bin.getId())
                .orElseThrow(() -> new IllegalStateException("Keine StockPosition für diesen Lagerplatz gefunden"));
        int previous = position.getQuantity() == null ? 0 : position.getQuantity();
        int currentReserved = position.getReservedQuantity() == null ? 0 : position.getReservedQuantity();
        position.setQuantity(physicalQuantity);
        position.setReservedQuantity(Math.max(0, currentReserved - reservationToRelease));
        stockPositionRepository.save(position);
        register(item, position);
        record(item, physicalQuantity - previous, previous, physicalQuantity,
                InventoryTransactionReason.MANUAL_ADJUSTMENT, referenceType, referenceId);
    }

    @Transactional
    public void recordStatusChange(InventoryItem item, String referenceType, Long referenceId) {
        int stock = totalStock(item);
        record(item, 0, stock, stock, InventoryTransactionReason.STATUS_CHANGE, referenceType, referenceId);
    }

    private void record(InventoryItem item, int delta, int previous, int current,
                        InventoryTransactionReason reason, String referenceType, Long referenceId) {
        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setTimestamp(LocalDateTime.now());
        transaction.setInventoryItem(item);
        transaction.setQuantityDelta(delta);
        transaction.setPreviousQuantity(previous);
        transaction.setNewQuantity(current);
        transaction.setReason(reason);
        transaction.setReferenceType(referenceType);
        transaction.setReferenceId(referenceId);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        transaction.setUsername(authentication == null || !authentication.isAuthenticated()
            ? "system" : authentication.getName());
        transactionRepository.save(transaction);
    }

    private java.util.List<StockPosition> positions(InventoryItem item) {
        return stockPositionRepository.findByInventoryItemId(item.getId());
    }

    private void register(InventoryItem item, StockPosition position) {
        if (item.getStockPositions() == null) item.setStockPositions(new java.util.ArrayList<>());
        if (!item.getStockPositions().contains(position)) item.getStockPositions().add(position);
    }

    private StockPosition createLegacyPosition(InventoryItem item) {
        StockPosition position = new StockPosition();
        position.setInventoryItem(item);
        position.setQuantity(0);
        position.setReservedQuantity(0);
        position.setStatus(StockStatus.AVAILABLE);
        return position;
    }

    public int totalStock(InventoryItem item) {
        return positions(item).stream().mapToInt(position -> position.getQuantity() == null ? 0 : position.getQuantity()).sum();
    }

    public int reservedStock(InventoryItem item) {
        return positions(item).stream().mapToInt(position -> position.getReservedQuantity() == null ? 0 : position.getReservedQuantity()).sum();
    }

    public int availableStock(InventoryItem item) {
        return totalStock(item) - reservedStock(item);
    }

    @Transactional
    public StockPosition ensurePosition(InventoryItem item, BinLocation bin) {
        return stockPositionRepository.findByInventoryItemIdAndBinLocationId(item.getId(), bin.getId())
                .orElseGet(() -> {
                    StockPosition position = createLegacyPosition(item);
                    position.setBinLocation(bin);
                    position.setInventoryItem(item);
                    return stockPositionRepository.save(position);
                });
    }
}
