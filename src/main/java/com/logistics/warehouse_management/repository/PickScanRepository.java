package com.logistics.warehouse_management.repository;

import com.logistics.warehouse_management.model.PickScan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PickScanRepository extends JpaRepository<PickScan, Long> {

    List<PickScan> findByPickOrderLineIdOrderByScannedAtDesc(Long pickOrderLineId);
}