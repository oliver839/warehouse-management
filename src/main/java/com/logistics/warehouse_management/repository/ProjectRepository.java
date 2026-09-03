package com.logistics.warehouse_management.repository;

import com.logistics.warehouse_management.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProjectRepository extends JpaRepository<Project, Long> {
}
