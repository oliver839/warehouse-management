package com.logistics.warehouse_management.repository;

import com.logistics.warehouse_management.model.PickOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PickOrderRepository extends JpaRepository<PickOrder, Long> {
}