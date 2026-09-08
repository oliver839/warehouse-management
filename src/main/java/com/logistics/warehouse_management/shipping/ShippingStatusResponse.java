package com.logistics.warehouse_management.shipping;

public record ShippingStatusResponse(String shipmentId, String trackingNumber, String status) {
}