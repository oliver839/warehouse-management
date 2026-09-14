package com.logistics.warehouse_management.controller;

import com.logistics.warehouse_management.model.StockPosition;
import com.logistics.warehouse_management.model.StockStatus;
import com.logistics.warehouse_management.service.StockStatusService;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stock-positions")
public class StockStatusController {

    private final StockStatusService service;

    public StockStatusController(StockStatusService service) {
        this.service = service;
    }

    @PatchMapping("/{id}/status")
    public StockPosition changeStatus(@PathVariable Long id,
                                      @RequestParam StockStatus status,
                                      @RequestParam(defaultValue = "MANUAL_STATUS_CHANGE") String reference) {
        return service.changeStatus(id, status, reference);
    }
}