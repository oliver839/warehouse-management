package com.logistics.warehouse_management.repository;

import com.logistics.warehouse_management.model.PickOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PickOrderLineRepository extends JpaRepository<PickOrderLine, Long> {

    List<PickOrderLine> findByPickOrderIdOrderByBinLocationCodeAsc(Long pickOrderId);
}