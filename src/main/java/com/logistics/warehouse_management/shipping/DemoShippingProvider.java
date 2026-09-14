package com.logistics.warehouse_management.shipping;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "shipping.provider", havingValue = "demo", matchIfMissing = true)
public class DemoShippingProvider implements ShippingProvider {

    private final java.util.concurrent.ConcurrentMap<String, ShipmentResponse> byIdempotencyKey =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();

    @Override
    public ShipmentResponse createShipment(ShipmentRequest request) {
        return createShipment(request, null);
    }

    @Override
    public ShipmentResponse createShipment(ShipmentRequest request, String idempotencyKey) {
        // Simulates a real carrier that deduplicates on Idempotency-Key:
        // the same key always returns the same shipment, never a second one.
        if (idempotencyKey != null) {
            return byIdempotencyKey.computeIfAbsent(idempotencyKey, key -> {
                calls.incrementAndGet();
                String id = "demo-" + UUID.randomUUID().toString().substring(0, 8);
                return new ShipmentResponse(id, "DEMO-" + id.substring(5).toUpperCase(), "CREATED");
            });
        }
        calls.incrementAndGet();
        String id = "demo-" + UUID.randomUUID().toString().substring(0, 8);
        return new ShipmentResponse(id, "DEMO-" + id.substring(5).toUpperCase(), "CREATED");
    }

    int providerCalls() {
        return calls.get();
    }

    public int getProviderCalls() {
        return calls.get();
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