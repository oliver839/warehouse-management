package com.logistics.warehouse_management.controller;

import com.logistics.warehouse_management.model.InventoryAdjustment;
import com.logistics.warehouse_management.service.InventoryAdjustmentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stock-adjustments")
public class InventoryAdjustmentController {

    private final InventoryAdjustmentService service;

    public InventoryAdjustmentController(InventoryAdjustmentService service) { this.service = service; }

    @PostMapping
    public InventoryAdjustment request(@RequestBody InventoryAdjustmentRequest request) { return service.request(request); }

    @GetMapping
    public List<InventoryAdjustment> getAll() { return service.getAll(); }

    @PostMapping("/{id}/approve")
    public InventoryAdjustment approve(@PathVariable Long id) { return service.approve(id); }

    @PostMapping("/{id}/reject")
    public InventoryAdjustment reject(@PathVariable Long id) { return service.reject(id); }
}