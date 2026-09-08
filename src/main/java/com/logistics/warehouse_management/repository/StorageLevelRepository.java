package com.logistics.warehouse_management.repository;

import com.logistics.warehouse_management.model.StorageLevel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StorageLevelRepository extends JpaRepository<StorageLevel, Long> {
}