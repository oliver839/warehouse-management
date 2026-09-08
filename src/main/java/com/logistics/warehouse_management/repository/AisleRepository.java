package com.logistics.warehouse_management.repository;

import com.logistics.warehouse_management.model.Aisle;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AisleRepository extends JpaRepository<Aisle, Long> {
}