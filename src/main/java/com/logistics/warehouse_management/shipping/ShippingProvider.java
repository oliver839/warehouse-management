package com.logistics.warehouse_management.shipping;

public interface ShippingProvider {

    ShipmentResponse createShipment(ShipmentRequest request);

    byte[] getLabel(String shipmentId);

    ShippingStatusResponse getStatus(String shipmentId);
}