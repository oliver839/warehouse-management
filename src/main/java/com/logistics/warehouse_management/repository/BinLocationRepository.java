package com.logistics.warehouse_management.repository;

import com.logistics.warehouse_management.model.BinLocation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BinLocationRepository extends JpaRepository<BinLocation, Long> {
}