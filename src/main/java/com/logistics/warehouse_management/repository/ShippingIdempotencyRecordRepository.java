package com.logistics.warehouse_management.repository;

import com.logistics.warehouse_management.model.ShippingIdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShippingIdempotencyRecordRepository extends JpaRepository<ShippingIdempotencyRecord, String> {
}
