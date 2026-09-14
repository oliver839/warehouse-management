package com.logistics.warehouse_management.repository;

import com.logistics.warehouse_management.model.InventoryAdjustment;
import com.logistics.warehouse_management.model.InventoryAdjustmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryAdjustmentRepository extends JpaRepository<InventoryAdjustment, Long> {

    List<InventoryAdjustment> findByStatusOrderByRequestedAtAsc(InventoryAdjustmentStatus status);
}