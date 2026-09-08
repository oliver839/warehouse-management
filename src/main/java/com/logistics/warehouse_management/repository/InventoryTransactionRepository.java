package com.logistics.warehouse_management.repository;

import com.logistics.warehouse_management.model.InventoryTransaction;
import com.logistics.warehouse_management.model.InventoryTransactionReason;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    List<InventoryTransaction> findByInventoryItemIdOrderByTimestampDesc(Long inventoryItemId);

        List<InventoryTransaction> findByInventoryItemIdAndTimestampBetweenOrderByTimestampDesc(
            Long inventoryItemId, LocalDateTime from, LocalDateTime to);

        List<InventoryTransaction> findByInventoryItemIdAndReasonOrderByTimestampDesc(
            Long inventoryItemId, InventoryTransactionReason reason);

        List<InventoryTransaction> findByInventoryItemIdAndReasonAndTimestampBetweenOrderByTimestampDesc(
            Long inventoryItemId, InventoryTransactionReason reason, LocalDateTime from, LocalDateTime to);
}