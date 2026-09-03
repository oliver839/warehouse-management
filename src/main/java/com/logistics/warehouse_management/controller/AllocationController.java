package com.logistics.warehouse_management.controller;

import com.logistics.warehouse_management.model.ProjectAllocation;
import com.logistics.warehouse_management.repository.ProjectAllocationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/allocations")
public class AllocationController {

    private final ProjectAllocationRepository projectAllocationRepository;

    public AllocationController(ProjectAllocationRepository projectAllocationRepository) {
        this.projectAllocationRepository = projectAllocationRepository;
    }

    @GetMapping
    public List<ProjectAllocation> getAllAllocations() {
        return projectAllocationRepository.findAll();
    }

    @PostMapping
    public ProjectAllocation createAllocation(@RequestBody ProjectAllocation allocation) {
        allocation.setId(null);
        return projectAllocationRepository.save(allocation);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAllocation(@PathVariable Long id) {
        if (!projectAllocationRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Zuweisung nicht gefunden");
        }
        projectAllocationRepository.deleteById(id);
    }
}
