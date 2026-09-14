package com.logistics.warehouse_management.controller;

public record InventoryAdjustmentRequest(Long stockPositionId, Integer requestedQuantity,
                                         String reason, String note) {
}