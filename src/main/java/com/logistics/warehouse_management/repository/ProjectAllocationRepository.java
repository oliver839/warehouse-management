package com.logistics.warehouse_management.repository;

import com.logistics.warehouse_management.model.ProjectAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProjectAllocationRepository extends JpaRepository<ProjectAllocation, Long> {

    List<ProjectAllocation> findByProjectId(Long projectId);
}
