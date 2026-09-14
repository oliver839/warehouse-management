package com.logistics.warehouse_management.service;

import com.logistics.warehouse_management.model.Batch;
import com.logistics.warehouse_management.model.InventoryItem;
import com.logistics.warehouse_management.model.StockAllocationStrategy;
import com.logistics.warehouse_management.model.StockPosition;
import com.logistics.warehouse_management.model.StockStatus;
import com.logistics.warehouse_management.repository.StockPositionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
public class StockAllocationService {

    private final StockPositionRepository positionRepository;

    public StockAllocationService(StockPositionRepository positionRepository) {
        this.positionRepository = positionRepository;
    }

    public List<StockPosition> findAllocatablePositions(InventoryItem item, int requestedQuantity,
                                                        StockAllocationStrategy strategy) {
        if (requestedQuantity <= 0) throw new IllegalArgumentException("Menge muss positiv sein");
        Comparator<StockPosition> comparator = switch (strategy) {
            case FEFO -> Comparator.comparing(this::expirationKey)
                    .thenComparing(this::receivedKey).thenComparing(StockPosition::getId);
            case FIFO -> Comparator.comparing(this::receivedKey).thenComparing(StockPosition::getId);
            case MANUAL -> Comparator.comparing(StockPosition::getId);
        };
        return positionRepository.findByInventoryItemId(item.getId()).stream()
                .filter(position -> position.getStatus() == StockStatus.AVAILABLE)
                .filter(position -> position.getBinLocation() == null
                        || (position.getBinLocation() != null && position.getBinLocation().isActive()))
                .filter(position -> available(position) > 0)
                .filter(position -> strategy != StockAllocationStrategy.FEFO || !isExpired(position.getBatch()))
                .sorted(comparator)
                .toList();
    }

    public List<StockPosition> allocateForPicking(InventoryItem item, int requestedQuantity,
                                                   StockAllocationStrategy strategy) {
        List<StockPosition> positions = findAllocatablePositions(item, requestedQuantity, strategy);
        int remaining = requestedQuantity;
        for (StockPosition position : positions) {
            remaining -= Math.min(remaining, available(position));
            if (remaining == 0) break;
        }
        if (remaining > 0) throw new IllegalStateException("Nicht genügend verfügbarer Bestand");
        return positions;
    }

    public List<StockPosition> findReservedPositionsForPicking(InventoryItem item, int requestedQuantity,
                                                               StockAllocationStrategy strategy) {
        // Gleicher Pickbarkeits-Begriff wie reserve(): AVAILABLE + (kein Bin = Bulk
        // oder aktiver Bin). Sonst sieht der Pick reservierte Bulk-Positionen nicht.
        List<StockPosition> positions = positionRepository.findByInventoryItemId(item.getId()).stream()
                .filter(position -> position.getStatus() == StockStatus.AVAILABLE)
                .filter(position -> position.getBinLocation() == null
                        || (position.getBinLocation() != null && position.getBinLocation().isActive()))
                .filter(position -> position.getReservedQuantity() != null && position.getReservedQuantity() > 0)
                .filter(position -> strategy != StockAllocationStrategy.FEFO || !isExpired(position.getBatch()))
                .sorted(strategy == StockAllocationStrategy.FEFO
                        ? Comparator.comparing(this::expirationKey).thenComparing(this::receivedKey).thenComparing(StockPosition::getId)
                        : Comparator.comparing(this::receivedKey).thenComparing(StockPosition::getId))
                .toList();
        int remaining = requestedQuantity;
        for (StockPosition position : positions) {
            remaining -= Math.min(remaining, position.getReservedQuantity());
            if (remaining == 0) break;
        }
        if (remaining > 0) throw new IllegalStateException("Nicht genügend reservierter Bestand verfügbar");
        return positions;
    }

    private int available(StockPosition position) {
        return Math.max(0, (position.getQuantity() == null ? 0 : position.getQuantity())
                - (position.getReservedQuantity() == null ? 0 : position.getReservedQuantity()));
    }

    private LocalDate expirationKey(StockPosition position) {
        return position.getBatch() == null || position.getBatch().getExpirationDate() == null
                ? LocalDate.MAX : position.getBatch().getExpirationDate();
    }

    private java.time.LocalDateTime receivedKey(StockPosition position) {
        return position.getBatch() == null || position.getBatch().getReceivedAt() == null
                ? java.time.LocalDateTime.MIN : position.getBatch().getReceivedAt();
    }

    private boolean isExpired(Batch batch) {
        return batch != null && batch.getExpirationDate() != null
                && batch.getExpirationDate().isBefore(LocalDate.now());
    }
}