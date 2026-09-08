package com.logistics.warehouse_management.service;

import java.util.List;

public class CsvImportException extends RuntimeException {

    private final List<String> errors;

    public CsvImportException(List<String> errors) {
        super("CSV-Import enthält ungültige Daten.");
        this.errors = errors;
    }

    public List<String> getErrors() {
        return errors;
    }
}