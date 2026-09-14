package com.logistics.warehouse_management.shipping;

public record ShippingOutboxPayload(Long deliveryNoteId, String reference,
                                    String recipient, String address, int packageCount) {
}