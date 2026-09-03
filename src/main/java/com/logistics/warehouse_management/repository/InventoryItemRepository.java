package com.logistics.warehouse_management.repository;

import com.logistics.warehouse_management.model.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {

    List<InventoryItem> findByWarehouseId(Long warehouseId);
}
