package com.logistics.warehouse_management.repository;

import com.logistics.warehouse_management.model.GoodsReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, Long> {
}