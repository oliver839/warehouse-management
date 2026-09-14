package com.logistics.warehouse_management.repository;

import com.logistics.warehouse_management.model.Batch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BatchRepository extends JpaRepository<Batch, Long> {
}