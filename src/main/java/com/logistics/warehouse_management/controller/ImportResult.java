package com.logistics.warehouse_management.controller;

import java.util.List;

public record ImportResult(int importedRows, List<String> errors) {
}