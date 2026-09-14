package com.logistics.warehouse_management.controller;

import com.logistics.warehouse_management.model.ShortageReason;

public record ShortageRequest(Integer physicallyFoundQuantity,
                              ShortageReason reason,
                              String note) {
}