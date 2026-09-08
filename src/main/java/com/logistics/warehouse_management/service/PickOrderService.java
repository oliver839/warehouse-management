package com.logistics.warehouse_management.service;

import com.logistics.warehouse_management.controller.PickScanRequest;
import com.logistics.warehouse_management.model.BinLocation;
import com.logistics.warehouse_management.model.PickLineStatus;
import com.logistics.warehouse_management.model.PickOrder;
import com.logistics.warehouse_management.model.PickOrderLine;
import com.logistics.warehouse_management.model.PickOrderStatus;
import com.logistics.warehouse_management.model.PickScan;
import com.logistics.warehouse_management.model.Project;
import com.logistics.warehouse_management.model.ProjectAllocation;
import com.logistics.warehouse_management.model.ProjectStatus;
import com.logistics.warehouse_management.repository.PickOrderLineRepository;
import com.logistics.warehouse_management.repository.PickOrderRepository;
import com.logistics.warehouse_management.repository.PickScanRepository;
import com.logistics.warehouse_management.repository.ProjectAllocationRepository;
import com.logistics.warehouse_management.repository.ProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class PickOrderService {

    private final PickOrderRepository pickOrderRepository;
    private final PickOrderLineRepository lineRepository;
    private final PickScanRepository scanRepository;
    private final ProjectRepository projectRepository;
    private final ProjectAllocationRepository allocationRepository;
    private final ProjectService projectService;
    private final PickScanService scanService;

    public PickOrderService(PickOrderRepository pickOrderRepository,
                            PickOrderLineRepository lineRepository,
                            PickScanRepository scanRepository,
                            ProjectRepository projectRepository,
                            ProjectAllocationRepository allocationRepository,
                            ProjectService projectService,
                            PickScanService scanService) {
        this.pickOrderRepository = pickOrderRepository;
        this.lineRepository = lineRepository;
        this.scanRepository = scanRepository;
        this.projectRepository = projectRepository;
        this.allocationRepository = allocationRepository;
        this.projectService = projectService;
        this.scanService = scanService;
    }

    @Transactional
    public PickOrder create(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> notFound("Auftrag nicht gefunden"));
        if (project.getStatus() != ProjectStatus.APPROVED && project.getStatus() != ProjectStatus.IN_PROGRESS) {
            throw badRequest("Ein Pickauftrag kann nur für einen freigegebenen oder laufenden Auftrag erstellt werden.");
        }
        List<ProjectAllocation> projectAllocations = allocationRepository.findByProjectId(projectId);
        if (projectAllocations.isEmpty()) {
            throw badRequest("Der Auftrag enthält keine Positionen.");
        }

        List<ProjectAllocation> allocations = projectAllocations.stream()
                .sorted(Comparator.comparing(allocation -> binCode(allocation.getInventoryItem().getBinLocation())))
                .toList();
        PickOrder pickOrder = new PickOrder();
        pickOrder.setProject(project);
        pickOrder.setStatus(PickOrderStatus.CREATED);
        pickOrder.setCreatedAt(LocalDateTime.now());
        for (ProjectAllocation allocation : allocations) {
            BinLocation bin = allocation.getInventoryItem().getBinLocation();
            if (bin == null || !bin.isActive()) {
                throw badRequest("Jede Auftragsposition benötigt einen aktiven Quell-Lagerplatz.");
            }
            PickOrderLine line = new PickOrderLine();
            line.setPickOrder(pickOrder);
            line.setInventoryItem(allocation.getInventoryItem());
            line.setRequiredQuantity(allocation.getAllocatedQuantity());
            line.setPickedQuantity(0);
            line.setBinLocation(bin);
            line.setStatus(PickLineStatus.OPEN);
            pickOrder.getLines().add(line);
        }
        return pickOrderRepository.save(pickOrder);
    }

    public List<PickOrder> getAll() {
        return pickOrderRepository.findAll();
    }

    @Transactional
    public PickOrder start(Long pickOrderId) {
        PickOrder pickOrder = requireOrder(pickOrderId);
        if (pickOrder.getStatus() != PickOrderStatus.CREATED) {
            throw badRequest("Nur neue Pickaufträge können gestartet werden.");
        }
        Project project = pickOrder.getProject();
        if (project.getStatus() == ProjectStatus.APPROVED) {
            projectService.changeStatus(project.getId(), ProjectStatus.IN_PROGRESS);
        }
        pickOrder.setStatus(PickOrderStatus.IN_PROGRESS);
        pickOrder.setStartedAt(LocalDateTime.now());
        return pickOrderRepository.save(pickOrder);
    }

    @Transactional
    public PickOrder scan(Long pickOrderId, Long lineId, PickScanRequest request) {
        PickOrder pickOrder = requireOrder(pickOrderId);
        PickOrderLine line = lineRepository.findById(lineId)
                .orElseThrow(() -> notFound("Pickposition nicht gefunden"));
        if (!line.getPickOrder().getId().equals(pickOrderId)) {
            throw badRequest("Die Pickposition gehört nicht zu diesem Pickauftrag.");
        }
        if (pickOrder.getStatus() != PickOrderStatus.IN_PROGRESS) {
            throw badRequest("Der Pickauftrag ist nicht aktiv.");
        }
        String barcode = request == null || request.barcode() == null ? "" : request.barcode().trim();
        int quantity = request == null || request.quantity() == null ? 1 : request.quantity();
        if (quantity <= 0) {
            rejectScan(line, barcode, quantity, "Die Scanmenge muss positiv sein.");
        }
        if (line.getInventoryItem().getBarcode() == null
                || !line.getInventoryItem().getBarcode().equalsIgnoreCase(barcode)) {
            rejectScan(line, barcode, quantity, "Falscher Barcode für diese Pickposition.");
        }
        int picked = line.getPickedQuantity() == null ? 0 : line.getPickedQuantity();
        int required = line.getRequiredQuantity() == null ? 0 : line.getRequiredQuantity();
        if (picked + quantity > required) {
            rejectScan(line, barcode, quantity, "Die Pickmenge überschreitet die Sollmenge.");
        }
        int newPicked = picked + quantity;
        line.setPickedQuantity(newPicked);
        line.setStatus(newPicked == required ? PickLineStatus.PICKED : PickLineStatus.PARTIALLY_PICKED);
        PickOrderLine savedLine = lineRepository.save(line);
        scanService.record(savedLine, barcode, quantity, true, "Scan akzeptiert");
        return pickOrderRepository.findById(pickOrderId).orElseThrow();
    }

    @Transactional
    public PickOrder complete(Long pickOrderId) {
        PickOrder pickOrder = requireOrder(pickOrderId);
        if (pickOrder.getStatus() != PickOrderStatus.IN_PROGRESS) {
            throw badRequest("Nur aktive Pickaufträge können abgeschlossen werden.");
        }
        if (lineRepository.findByPickOrderIdOrderByBinLocationCodeAsc(pickOrderId).stream()
                .anyMatch(line -> line.getStatus() != PickLineStatus.PICKED)) {
            throw badRequest("Alle Pickpositionen müssen vollständig gepickt sein.");
        }
        pickOrder.setStatus(PickOrderStatus.COMPLETED);
        pickOrder.setCompletedAt(LocalDateTime.now());
        return pickOrderRepository.save(pickOrder);
    }

    public List<PickOrderLine> getLines(Long pickOrderId) {
        requireOrder(pickOrderId);
        return lineRepository.findByPickOrderIdOrderByBinLocationCodeAsc(pickOrderId);
    }

    public List<PickScan> getScans(Long pickOrderId, Long lineId) {
        PickOrderLine line = lineRepository.findById(lineId)
                .orElseThrow(() -> notFound("Pickposition nicht gefunden"));
        if (!line.getPickOrder().getId().equals(pickOrderId)) {
            throw badRequest("Die Pickposition gehört nicht zu diesem Pickauftrag.");
        }
        return scanRepository.findByPickOrderLineIdOrderByScannedAtDesc(lineId);
    }

    private void rejectScan(PickOrderLine line, String barcode, int quantity, String message) {
        scanService.record(line, barcode, quantity, false, message);
        throw badRequest(message);
    }

    private PickOrder requireOrder(Long id) {
        return pickOrderRepository.findById(id)
                .orElseThrow(() -> notFound("Pickauftrag nicht gefunden"));
    }

    private String binCode(BinLocation bin) {
        return bin == null || bin.getCode() == null ? "" : bin.getCode();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}