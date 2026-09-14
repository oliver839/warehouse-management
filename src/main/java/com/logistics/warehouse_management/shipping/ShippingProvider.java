package com.logistics.warehouse_management.shipping;

public interface ShippingProvider {

    ShipmentResponse createShipment(ShipmentRequest request);

    default ShipmentResponse createShipment(ShipmentRequest request, String idempotencyKey) {
        return createShipment(request);
    }

    byte[] getLabel(String shipmentId);

    ShippingStatusResponse getStatus(String shipmentId);
}