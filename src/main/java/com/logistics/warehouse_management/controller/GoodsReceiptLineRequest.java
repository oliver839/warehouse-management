package com.logistics.warehouse_management.controller;

public record GoodsReceiptLineRequest(Long inventoryItemId,
                                      Integer expectedQuantity,
                                      Integer receivedQuantity,
                                      Long binLocationId) {
}