package com.logistics.warehouse_management.repository;

import com.logistics.warehouse_management.model.ProjectAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProjectAllocationRepository extends JpaRepository<ProjectAllocation, Long> {

    @Query("SELECT p FROM ProjectAllocation p WHERE p.status = 'DELIVERED'")
    List<ProjectAllocation> findDeliveredAllocations();
}
