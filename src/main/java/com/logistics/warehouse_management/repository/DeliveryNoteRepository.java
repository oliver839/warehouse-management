package com.logistics.warehouse_management.repository;

import com.logistics.warehouse_management.model.DeliveryNote;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryNoteRepository extends JpaRepository<DeliveryNote, Long> {
}
