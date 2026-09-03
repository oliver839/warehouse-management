package com.logistics.warehouse_management.controller;

import com.logistics.warehouse_management.model.Project;
import com.logistics.warehouse_management.model.ProjectAllocation;
import com.logistics.warehouse_management.model.ProjectStatus;
import com.logistics.warehouse_management.model.InventoryItem;
import com.logistics.warehouse_management.repository.InventoryItemRepository;
import com.logistics.warehouse_management.repository.ProjectAllocationRepository;
import com.logistics.warehouse_management.repository.ProjectRepository;
import com.logistics.warehouse_management.service.ProjectService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectRepository projectRepository;
    private final ProjectAllocationRepository projectAllocationRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final ProjectService projectService;

    public ProjectController(ProjectRepository projectRepository,
                             ProjectAllocationRepository projectAllocationRepository,
                             InventoryItemRepository inventoryItemRepository,
                             ProjectService projectService) {
        this.projectRepository = projectRepository;
        this.projectAllocationRepository = projectAllocationRepository;
        this.inventoryItemRepository = inventoryItemRepository;
        this.projectService = projectService;
    }

    @GetMapping
    public List<Project> getAllProjects() {
        return projectRepository.findAll();
    }

    @PostMapping
    public Project createProject(@RequestBody Project project) {
        project.setId(null);
        project.setStatus(ProjectStatus.PENDING);
        return projectRepository.save(project);
    }

    @PostMapping("/{id}/allocations")
    public ResponseEntity<?> addAllocation(@PathVariable Long id, @RequestBody AllocationRequest request) {
        if (request.inventoryItemId() == null || request.allocatedQuantity() == null || request.allocatedQuantity() <= 0) {
            return ResponseEntity.badRequest().body("Item und positive Menge sind erforderlich.");
        }
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Auftrag nicht gefunden"));
        if (project.getStatus() != ProjectStatus.PENDING) {
            return ResponseEntity.badRequest().body("Ressourcen können nur einem ausstehenden Auftrag zugewiesen werden.");
        }
        InventoryItem item = inventoryItemRepository.findById(request.inventoryItemId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item nicht gefunden"));

        ProjectAllocation allocation = new ProjectAllocation();
        allocation.setProject(project);
        allocation.setInventoryItem(item);
        allocation.setAllocatedQuantity(request.allocatedQuantity());
        return ResponseEntity.ok(projectAllocationRepository.save(allocation));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> changeStatus(@PathVariable Long id, @RequestParam ProjectStatus newStatus) {
        try {
            return ResponseEntity.ok(projectService.changeStatus(id, newStatus));
        } catch (IllegalStateException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProject(@PathVariable Long id) {
        if (!projectRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Projekt nicht gefunden");
        }
        projectRepository.deleteById(id);
    }
}
