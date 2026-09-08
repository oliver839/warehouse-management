package com.logistics.warehouse_management.repository;

import com.logistics.warehouse_management.model.Rack;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RackRepository extends JpaRepository<Rack, Long> {
}