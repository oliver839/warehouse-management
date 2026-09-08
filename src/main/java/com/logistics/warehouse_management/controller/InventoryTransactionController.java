package com.logistics.warehouse_management.controller;

import com.logistics.warehouse_management.model.InventoryTransaction;
import com.logistics.warehouse_management.model.InventoryTransactionReason;
import com.logistics.warehouse_management.repository.InventoryTransactionRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/items/{itemId}/transactions")
public class InventoryTransactionController {

    private final InventoryTransactionRepository transactionRepository;

    public InventoryTransactionController(InventoryTransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @GetMapping
    public List<InventoryTransaction> getTransactions(@PathVariable Long itemId,
                                                      @org.springframework.web.bind.annotation.RequestParam(required = false) LocalDateTime from,
                                                      @org.springframework.web.bind.annotation.RequestParam(required = false) LocalDateTime to,
                                                      @org.springframework.web.bind.annotation.RequestParam(required = false) InventoryTransactionReason reason) {
        if (from != null && to != null && reason != null) {
            return transactionRepository.findByInventoryItemIdAndReasonAndTimestampBetweenOrderByTimestampDesc(itemId, reason, from, to);
        }
        if (from != null && to != null) {
            return transactionRepository.findByInventoryItemIdAndTimestampBetweenOrderByTimestampDesc(itemId, from, to);
        }
        if (reason != null) {
            return transactionRepository.findByInventoryItemIdAndReasonOrderByTimestampDesc(itemId, reason);
        }
        return transactionRepository.findByInventoryItemIdOrderByTimestampDesc(itemId);
    }
}