package com.logistics.warehouse_management.repository;

import com.logistics.warehouse_management.model.Tool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ToolRepository extends JpaRepository<Tool, Long> {

    @Query("SELECT t FROM Tool t WHERE t.warehouse.id = :warehouseId")
    List<Tool> findToolsByWarehouseId(@Param("warehouseId") Long warehouseId);
}
