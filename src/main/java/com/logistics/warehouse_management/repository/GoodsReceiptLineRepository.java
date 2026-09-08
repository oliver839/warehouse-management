package com.logistics.warehouse_management.repository;

import com.logistics.warehouse_management.model.GoodsReceiptLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GoodsReceiptLineRepository extends JpaRepository<GoodsReceiptLine, Long> {

    List<GoodsReceiptLine> findByGoodsReceiptId(Long goodsReceiptId);
}