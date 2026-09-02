package com.logistics.warehouse_management.repository;

import com.logistics.warehouse_management.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    @Query("SELECT DISTINCT p FROM Project p JOIN p.allocations a JOIN a.deliveryNote d WHERE d.isConfirmed = false")
    List<Project> findProjectsWithUnconfirmedDeliveryNotes();
}
