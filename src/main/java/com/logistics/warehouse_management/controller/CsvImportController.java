package com.logistics.warehouse_management.controller;

import com.logistics.warehouse_management.service.CsvImportException;
import com.logistics.warehouse_management.service.CsvImportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/import")
public class CsvImportController {

    private final CsvImportService service;

    public CsvImportController(CsvImportService service) {
        this.service = service;
    }

    @PostMapping("/items")
    public ResponseEntity<ImportResult> importItems(@RequestPart("file") MultipartFile file) {
        return importFile(() -> service.importItems(file));
    }

    @PostMapping("/projects")
    public ResponseEntity<ImportResult> importProjects(@RequestPart("file") MultipartFile file) {
        return importFile(() -> service.importProjects(file));
    }

    @PostMapping("/project-lines")
    public ResponseEntity<ImportResult> importProjectLines(@RequestPart("file") MultipartFile file) {
        return importFile(() -> service.importProjectLines(file));
    }

    private ResponseEntity<ImportResult> importFile(ImportAction action) {
        try {
            return ResponseEntity.ok(new ImportResult(action.run(), List.of()));
        } catch (CsvImportException exception) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ImportResult(0, exception.getErrors()));
        }
    }

    @FunctionalInterface
    private interface ImportAction {
        int run();
    }
}