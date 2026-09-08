package com.logistics.warehouse_management.controller;

public record PickScanRequest(String barcode, Integer quantity) {
}