package com.logistics.warehouse_management.controller;

public record AllocationRequest(Long inventoryItemId, Integer allocatedQuantity) {
}
