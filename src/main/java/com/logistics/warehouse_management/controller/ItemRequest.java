package com.logistics.warehouse_management.controller;

public record ItemRequest(
        String sku,
        String barcode,
        String name,
        Integer quantityInStock,
        Long warehouseId,
        Double spacePerUnit,
        Double weightPerUnit,
        Double length,
        Double width,
        Double height,
        String type,
        String serialNumber,
        Boolean isCalibrated,
        String materialType,
        String unit,
        Long binLocationId
) {
}
