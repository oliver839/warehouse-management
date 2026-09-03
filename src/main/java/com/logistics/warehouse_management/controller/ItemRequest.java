package com.logistics.warehouse_management.controller;

public record ItemRequest(
        String name,
        Integer quantityInStock,
        Long warehouseId,
        Double spacePerUnit,
        String type,
        String serialNumber,
        Boolean isCalibrated,
        String materialType,
        String unit
) {
}
