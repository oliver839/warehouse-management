package com.logistics.warehouse_management.shipping;

public record ShipmentRequest(String reference, String recipient, String address, int packageCount) {
}