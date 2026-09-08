package com.logistics.warehouse_management.shipping;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "shipping.provider", havingValue = "demo", matchIfMissing = true)
public class DemoShippingProvider implements ShippingProvider {

    @Override
    public ShipmentResponse createShipment(ShipmentRequest request) {
        String id = "demo-" + UUID.randomUUID().toString().substring(0, 8);
        return new ShipmentResponse(id, "DEMO-" + id.substring(5).toUpperCase(), "CREATED");
    }

    @Override
    public byte[] getLabel(String shipmentId) {
        return ("DEMO SHIPPING LABEL\nShipment: " + shipmentId).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public ShippingStatusResponse getStatus(String shipmentId) {
        return new ShippingStatusResponse(shipmentId, "DEMO-" + shipmentId.substring(Math.max(0, shipmentId.length() - 8)).toUpperCase(), "CREATED");
    }
}