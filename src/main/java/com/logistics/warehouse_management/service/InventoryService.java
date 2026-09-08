package com.logistics.warehouse_management.service;

import com.logistics.warehouse_management.model.InventoryItem;
import com.logistics.warehouse_management.model.BinLocation;
import com.logistics.warehouse_management.model.InventoryTransaction;
import com.logistics.warehouse_management.model.InventoryTransactionReason;
import com.logistics.warehouse_management.repository.InventoryItemRepository;
import com.logistics.warehouse_management.repository.InventoryTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;

@Service
public class InventoryService {

    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryTransactionRepository transactionRepository;

    public InventoryService(InventoryItemRepository inventoryItemRepository,
                            InventoryTransactionRepository transactionRepository) {
        this.inventoryItemRepository = inventoryItemRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public void reserve(InventoryItem item, int quantity) {
        if (quantity > item.getAvailableQuantity()) {
            throw new IllegalStateException("Nicht genug Bestand verfügbar");
        }
        item.setReservedQuantity(getReservedQuantity(item) + quantity);
        inventoryItemRepository.save(item);
        record(item, 0, currentStock(item), currentStock(item), InventoryTransactionReason.RESERVATION, null, null);
    }

    @Transactional
    public void releaseReservation(InventoryItem item, int quantity) {
        item.setReservedQuantity(Math.max(0, getReservedQuantity(item) - quantity));
        inventoryItemRepository.save(item);
        record(item, 0, currentStock(item), currentStock(item), InventoryTransactionReason.RELEASE, null, null);
    }

    @Transactional
    public void consumeReservedStock(InventoryItem item, int quantity) {
        consumeReservedStock(item, quantity, null, null);
    }

    @Transactional
    public void consumeReservedStock(InventoryItem item, int quantity,
                                     String referenceType, Long referenceId) {
        int stock = item.getQuantityInStock() == null ? 0 : item.getQuantityInStock();
        if (quantity > getReservedQuantity(item) || quantity > stock) {
            throw new IllegalStateException("Reservierter Bestand ist nicht verfügbar");
        }
        item.setQuantityInStock(stock - quantity);
        item.setReservedQuantity(getReservedQuantity(item) - quantity);
        inventoryItemRepository.save(item);
        record(item, -quantity, stock, stock - quantity, InventoryTransactionReason.SHIPMENT, referenceType, referenceId);
    }

    @Transactional
    public void receive(InventoryItem item, int quantity, BinLocation bin,
                        String referenceType, Long referenceId) {
        if (quantity <= 0) {
            throw new IllegalStateException("Die Liefermenge muss positiv sein");
        }
        int stock = currentStock(item);
        if (item.getBinLocation() != null && !item.getBinLocation().getId().equals(bin.getId()) && stock > 0) {
            throw new IllegalStateException("Ein Artikel mit bestehendem Bestand kann nicht gleichzeitig in mehrere Bins gelegt werden");
        }
        item.setBinLocation(bin);
        item.setWarehouse(bin.getWarehouse());
        item.setQuantityInStock(stock + quantity);
        inventoryItemRepository.save(item);
        record(item, quantity, stock, stock + quantity, InventoryTransactionReason.GOODS_RECEIPT, referenceType, referenceId);
    }

    @Transactional
    public void adjustStock(InventoryItem item, int newQuantity,
                            InventoryTransactionReason reason, String referenceType, Long referenceId) {
        if (newQuantity < 0) {
            throw new IllegalArgumentException("Bestand darf nicht negativ sein");
        }
        int previous = currentStock(item);
        if (previous == newQuantity) {
            return;
        }
        item.setQuantityInStock(newQuantity);
        inventoryItemRepository.save(item);
        record(item, newQuantity - previous, previous, newQuantity, reason, referenceType, referenceId);
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

    private int currentStock(InventoryItem item) {
        return item.getQuantityInStock() == null ? 0 : item.getQuantityInStock();
    }

    private int getReservedQuantity(InventoryItem item) {
        return item.getReservedQuantity() == null ? 0 : item.getReservedQuantity();
    }
}
