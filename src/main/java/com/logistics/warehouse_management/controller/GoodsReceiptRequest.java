package com.logistics.warehouse_management.controller;

public record GoodsReceiptRequest(String receiptNumber, String supplierName, String receivedBy) {
}