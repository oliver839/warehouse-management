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
import com.logistics.warehouse_management.model.StockPosition;
import com.logistics.warehouse_management.model.StockAllocationStrategy;
import com.logistics.warehouse_management.model.ShortageReason;
import com.logistics.warehouse_management.repository.PickOrderLineRepository;
import com.logistics.warehouse_management.repository.PickOrderRepository;
import com.logistics.warehouse_management.repository.PickScanRepository;
import com.logistics.warehouse_management.repository.ProjectAllocationRepository;
import com.logistics.warehouse_management.repository.ProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final InventoryService inventoryService;
    private final StockAllocationService stockAllocationService;

    public PickOrderService(PickOrderRepository pickOrderRepository,
                            PickOrderLineRepository lineRepository,
                            PickScanRepository scanRepository,
                            ProjectRepository projectRepository,
                            ProjectAllocationRepository allocationRepository,
                            ProjectService projectService,
                            PickScanService scanService,
                            InventoryService inventoryService,
                            StockAllocationService stockAllocationService) {
        this.pickOrderRepository = pickOrderRepository;
        this.lineRepository = lineRepository;
        this.scanRepository = scanRepository;
        this.projectRepository = projectRepository;
        this.allocationRepository = allocationRepository;
        this.projectService = projectService;
        this.scanService = scanService;
        this.inventoryService = inventoryService;
        this.stockAllocationService = stockAllocationService;
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

        List<ProjectAllocation> allocations = projectAllocations;
        PickOrder pickOrder = new PickOrder();
        pickOrder.setProject(project);
        pickOrder.setStatus(PickOrderStatus.CREATED);
        pickOrder.setCreatedAt(LocalDateTime.now());
        for (ProjectAllocation allocation : allocations) {
            int remaining = allocation.getAllocatedQuantity();
                List<StockPosition> positions;
                try {
                    positions = stockAllocationService.findReservedPositionsForPicking(
                        allocation.getInventoryItem(), remaining, StockAllocationStrategy.FEFO);
                } catch (IllegalStateException exception) {
                    int reservedTotal = inventoryService.reservedStock(allocation.getInventoryItem());
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Keine kommissionierfähige Reservierung für "
                                    + allocation.getInventoryItem().getName()
                                    + " (reserviert gesamt: " + reservedTotal
                                    + ", benötigt: " + remaining
                                    + "). Bitte Bestand einbuchen (Wareneingang) und Auftrag erneut genehmigen.");
                }
                positions = positions.stream()
                    .sorted(Comparator.comparing(position -> binCode(position.getBinLocation())))
                    .toList();
            for (StockPosition position : positions) {
                int quantity = Math.min(remaining, position.getReservedQuantity());
                PickOrderLine line = new PickOrderLine();
                line.setPickOrder(pickOrder);
                line.setInventoryItem(allocation.getInventoryItem());
                line.setStockPosition(position);
                line.setBatch(position.getBatch());
                line.setRequiredQuantity(quantity);
                line.setPickedQuantity(0);
                line.setBinLocation(position.getBinLocation());
                line.setStatus(PickLineStatus.OPEN);
                pickOrder.getLines().add(line);
                remaining -= quantity;
                if (remaining == 0) break;
            }
            if (remaining > 0) {
                throw badRequest("Nicht genügend reservierter Bestand auf aktiven Lagerplätzen verfügbar.");
            }
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
                .anyMatch(line -> line.getStatus() != PickLineStatus.PICKED
                        && line.getStatus() != PickLineStatus.SHORTAGE_REPORTED)) {
            throw badRequest("Alle Pickpositionen müssen gepickt oder als Fehlmenge gemeldet sein.");
        }
        pickOrder.setStatus(PickOrderStatus.COMPLETED);
        pickOrder.setCompletedAt(LocalDateTime.now());
        return pickOrderRepository.save(pickOrder);
    }

    @Transactional
    public PickOrder reportShortage(Long pickOrderId, Long lineId, com.logistics.warehouse_management.controller.ShortageRequest request) {
        PickOrder pickOrder = requireOrder(pickOrderId);
        PickOrderLine line = lineRepository.findById(lineId)
                .orElseThrow(() -> notFound("Pickposition nicht gefunden"));
        if (!line.getPickOrder().getId().equals(pickOrderId)) {
            throw badRequest("Die Pickposition gehört nicht zu diesem Pickauftrag.");
        }
        if (pickOrder.getStatus() != PickOrderStatus.IN_PROGRESS) {
            throw badRequest("Der Pickauftrag ist nicht aktiv.");
        }
        if (line.getStatus() == PickLineStatus.PICKED || line.getStatus() == PickLineStatus.SHORTAGE_REPORTED) {
            throw badRequest("Für diese Pickposition wurde bereits ein Ergebnis erfasst.");
        }
        if (request == null || request.physicallyFoundQuantity() == null || request.reason() == null) {
            throw badRequest("Gefundene Menge und Fehlmengen-Grund sind erforderlich.");
        }
        int found = request.physicallyFoundQuantity();
        int picked = line.getPickedQuantity() == null ? 0 : line.getPickedQuantity();
        int required = line.getRequiredQuantity() == null ? 0 : line.getRequiredQuantity();
        if (found < 0 || found > required || found < picked) {
            throw badRequest("Die gefundene Menge muss zwischen der bereits gepickten und der Sollmenge liegen.");
        }
        int shortage = required - found;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        line.setPickedQuantity(found);
        line.setShortageQuantity(shortage);
        line.setShortageReason(request.reason());
        line.setShortageNote(request.note());
        line.setShortageReportedAt(LocalDateTime.now());
        line.setShortageReportedBy(authentication == null ? "system" : authentication.getName());
        line.setStatus(shortage == 0 ? PickLineStatus.PICKED : PickLineStatus.SHORTAGE_REPORTED);
        lineRepository.save(line);
        if (line.getBinLocation() != null && (shortage > 0 || found > 0)) {
            inventoryService.reconcileShortage(line.getInventoryItem(), line.getBinLocation(), found,
                    shortage, "PICK_LINE", line.getId());
        }
        return pickOrderRepository.findById(pickOrderId).orElseThrow();
    }

    public List<PickOrderLine> getShortages(Long pickOrderId) {
        requireOrder(pickOrderId);
        return lineRepository.findByPickOrderIdOrderByBinLocationCodeAsc(pickOrderId).stream()
                .filter(line -> line.getStatus() == PickLineStatus.SHORTAGE_REPORTED)
                .toList();
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