package com.logistics.warehouse_management.service;

import com.logistics.warehouse_management.model.Project;
import com.logistics.warehouse_management.model.ProjectAllocation;
import com.logistics.warehouse_management.model.ProjectStatus;
import com.logistics.warehouse_management.repository.ProjectAllocationRepository;
import com.logistics.warehouse_management.repository.ProjectRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectAllocationRepository projectAllocationRepository;
    private final InventoryService inventoryService;

    public ProjectService(ProjectRepository projectRepository,
                          ProjectAllocationRepository projectAllocationRepository,
                          InventoryService inventoryService) {
        this.projectRepository = projectRepository;
        this.projectAllocationRepository = projectAllocationRepository;
        this.inventoryService = inventoryService;
    }

    @Transactional
    public Project changeStatus(Long projectId, ProjectStatus newStatus) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Auftrag nicht gefunden"));
        ProjectStatus oldStatus = project.getStatus() == null ? ProjectStatus.PENDING : project.getStatus();
        if (oldStatus == newStatus) {
            return project;
        }

        List<ProjectAllocation> allocations = projectAllocationRepository.findByProjectId(projectId);
        switch (newStatus) {
            case APPROVED, IN_PROGRESS -> {
                if (oldStatus == ProjectStatus.COMPLETED) {
                    throw new IllegalStateException("Ein abgeschlossener Auftrag kann nicht erneut aktiviert werden");
                }
                if (!isActive(oldStatus)) {
                    allocations.forEach(allocation -> inventoryService.reserve(
                            allocation.getInventoryItem(), allocation.getAllocatedQuantity()));
                }
            }
            case COMPLETED -> {
                if (!isActive(oldStatus)) {
                    throw new IllegalStateException("Nur ein genehmigter oder laufender Auftrag kann abgeschlossen werden");
                }
                allocations.forEach(allocation -> inventoryService.consumeReservedStock(
                        allocation.getInventoryItem(), allocation.getAllocatedQuantity()));
            }
            case REJECTED -> {
                if (isActive(oldStatus)) {
                    allocations.forEach(allocation -> inventoryService.releaseReservation(
                            allocation.getInventoryItem(), allocation.getAllocatedQuantity()));
                }
            }
            case PENDING -> throw new IllegalStateException("Der Status kann nicht auf PENDING zurückgesetzt werden");
        }

        project.setStatus(newStatus);
        return projectRepository.save(project);
    }

    private boolean isActive(ProjectStatus status) {
        return status == ProjectStatus.APPROVED || status == ProjectStatus.IN_PROGRESS;
    }
}
