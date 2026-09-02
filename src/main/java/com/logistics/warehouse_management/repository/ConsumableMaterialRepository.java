package com.logistics.warehouse_management.repository;

import com.logistics.warehouse_management.model.ConsumableMaterial;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsumableMaterialRepository extends JpaRepository<ConsumableMaterial, Long> {
}
