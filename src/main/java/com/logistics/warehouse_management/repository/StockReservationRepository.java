package com.logistics.warehouse_management.repository;

import com.logistics.warehouse_management.model.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    List<StockReservation> findByStockPositionIdOrderByCreatedAtDesc(Long stockPositionId);
}