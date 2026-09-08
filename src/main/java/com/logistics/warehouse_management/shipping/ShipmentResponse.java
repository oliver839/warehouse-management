package com.logistics.warehouse_management.shipping;

public record ShipmentResponse(String shipmentId, String trackingNumber, String status) {
}