package com.logistics.warehouse_management.service;

import com.logistics.warehouse_management.model.ConsumableMaterial;
import com.logistics.warehouse_management.model.Project;
import com.logistics.warehouse_management.model.ProjectAllocation;
import com.logistics.warehouse_management.model.ProjectStatus;
import com.logistics.warehouse_management.model.Tool;
import com.logistics.warehouse_management.model.Warehouse;
import com.logistics.warehouse_management.repository.InventoryItemRepository;
import com.logistics.warehouse_management.repository.ProjectAllocationRepository;
import com.logistics.warehouse_management.repository.ProjectRepository;
import com.logistics.warehouse_management.repository.WarehouseRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class CsvImportService {

    private final InventoryItemRepository itemRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProjectRepository projectRepository;
    private final ProjectAllocationRepository allocationRepository;

    public CsvImportService(InventoryItemRepository itemRepository,
                            WarehouseRepository warehouseRepository,
                            ProjectRepository projectRepository,
                            ProjectAllocationRepository allocationRepository) {
        this.itemRepository = itemRepository;
        this.warehouseRepository = warehouseRepository;
        this.projectRepository = projectRepository;
        this.allocationRepository = allocationRepository;
    }

    @Transactional
    public int importItems(MultipartFile file) {
        List<String> errors = new ArrayList<>();
        List<CSVRecord> records = parse(file, Set.of("sku", "name", "quantityInStock", "spacePerUnit", "warehouseId", "type"), errors);
        Set<String> skus = new HashSet<>();
        int imported = 0;
        for (CSVRecord record : records) {
            try {
                String sku = value(record, "sku");
                if (!skus.add(sku) || itemRepository.existsBySkuIgnoreCase(sku)) {
                    throw new IllegalArgumentException("SKU ist bereits vorhanden");
                }
                String barcode = optional(record, "barcode");
                if (barcode != null && itemRepository.existsByBarcode(barcode)) {
                    throw new IllegalArgumentException("Barcode ist bereits vorhanden");
                }
                Warehouse warehouse = warehouseRepository.findById(Long.valueOf(value(record, "warehouseId")))
                        .orElseThrow(() -> new IllegalArgumentException("Lager nicht gefunden"));
                var item = "CONSUMABLE".equalsIgnoreCase(value(record, "type"))
                        ? new ConsumableMaterial() : new Tool();
                item.setSku(sku);
                item.setBarcode(barcode);
                item.setName(value(record, "name"));
                item.setQuantityInStock(Integer.valueOf(value(record, "quantityInStock")));
                item.setSpacePerUnit(Double.valueOf(value(record, "spacePerUnit")));
                item.setWarehouse(warehouse);
                itemRepository.save(item);
                imported++;
            } catch (RuntimeException exception) {
                errors.add(error(record, exception.getMessage()));
            }
        }
        failIfErrors(errors);
        return imported;
    }

    @Transactional
    public int importProjects(MultipartFile file) {
        List<String> errors = new ArrayList<>();
        List<CSVRecord> records = parse(file, Set.of("orderNumber", "name", "description"), errors);
        Set<String> orderNumbers = new HashSet<>();
        int imported = 0;
        for (CSVRecord record : records) {
            try {
                String orderNumber = value(record, "orderNumber");
                if (!orderNumbers.add(orderNumber) || projectRepository.existsByOrderNumberIgnoreCase(orderNumber)) {
                    throw new IllegalArgumentException("Auftragsnummer ist bereits vorhanden");
                }
                Project project = new Project();
                project.setOrderNumber(orderNumber);
                project.setName(value(record, "name"));
                project.setDescription(optional(record, "description"));
                project.setCustomerName(optional(record, "customerName"));
                project.setCustomerEmail(optional(record, "customerEmail"));
                project.setDeliveryAddress(optional(record, "deliveryAddress"));
                project.setStatus(ProjectStatus.PENDING);
                projectRepository.save(project);
                imported++;
            } catch (RuntimeException exception) {
                errors.add(error(record, exception.getMessage()));
            }
        }
        failIfErrors(errors);
        return imported;
    }

    @Transactional
    public int importProjectLines(MultipartFile file) {
        List<String> errors = new ArrayList<>();
        List<CSVRecord> records = parse(file, Set.of("orderNumber", "sku", "quantity"), errors);
        int imported = 0;
        for (CSVRecord record : records) {
            try {
                Project project = projectRepository.findByOrderNumberIgnoreCase(value(record, "orderNumber"))
                        .orElseThrow(() -> new IllegalArgumentException("Auftrag nicht gefunden"));
                if (project.getStatus() != ProjectStatus.PENDING) {
                    throw new IllegalArgumentException("Positionen können nur zu ausstehenden Aufträgen importiert werden");
                }
                var item = itemRepository.findBySkuIgnoreCase(value(record, "sku"))
                        .orElseThrow(() -> new IllegalArgumentException("Artikel nicht gefunden"));
                int quantity = Integer.parseInt(value(record, "quantity"));
                if (quantity <= 0) {
                    throw new IllegalArgumentException("Menge muss positiv sein");
                }
                ProjectAllocation allocation = new ProjectAllocation();
                allocation.setProject(project);
                allocation.setInventoryItem(item);
                allocation.setAllocatedQuantity(quantity);
                allocationRepository.save(allocation);
                imported++;
            } catch (RuntimeException exception) {
                errors.add(error(record, exception.getMessage()));
            }
        }
        failIfErrors(errors);
        return imported;
    }

    private List<CSVRecord> parse(MultipartFile file, Set<String> requiredHeaders, List<String> errors) {
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(true).build();
            CSVParser parser = format.parse(reader);
            Set<String> headers = parser.getHeaderMap().keySet();
            for (String required : requiredHeaders) {
                if (!headers.contains(required)) {
                    errors.add("Fehlende CSV-Spalte: " + required);
                }
            }
            if (!errors.isEmpty()) {
                parser.close();
                return List.of();
            }
            List<CSVRecord> records = parser.getRecords();
            parser.close();
            return records;
        } catch (IOException exception) {
            errors.add("CSV-Datei konnte nicht gelesen werden: " + exception.getMessage());
            return List.of();
        }
    }

    private String value(CSVRecord record, String column) {
        String value = record.get(column);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Spalte " + column + " darf nicht leer sein");
        }
        return value.trim();
    }

    private String optional(CSVRecord record, String column) {
        return record.isMapped(column) && !record.get(column).isBlank() ? record.get(column).trim() : null;
    }

    private String error(CSVRecord record, String message) {
        return "Zeile " + (record.getRecordNumber() + 1) + ": " + (message == null ? "ungültige Daten" : message);
    }

    private void failIfErrors(List<String> errors) {
        if (!errors.isEmpty()) {
            throw new CsvImportException(errors);
        }
    }
}