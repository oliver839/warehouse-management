package com.logistics.warehouse_management.repository;

import com.logistics.warehouse_management.model.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
}
