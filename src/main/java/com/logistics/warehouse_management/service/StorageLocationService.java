package com.logistics.warehouse_management.service;

import com.logistics.warehouse_management.model.BinLocation;
import com.logistics.warehouse_management.model.InventoryItem;
import com.logistics.warehouse_management.model.WarehouseZone;
import com.logistics.warehouse_management.model.Aisle;
import com.logistics.warehouse_management.model.Rack;
import com.logistics.warehouse_management.model.StorageLevel;
import com.logistics.warehouse_management.repository.AisleRepository;
import com.logistics.warehouse_management.repository.BinLocationRepository;
import com.logistics.warehouse_management.repository.InventoryItemRepository;
import com.logistics.warehouse_management.repository.RackRepository;
import com.logistics.warehouse_management.repository.StorageLevelRepository;
import com.logistics.warehouse_management.repository.WarehouseZoneRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StorageLocationService {

    private final WarehouseZoneRepository zoneRepository;
    private final AisleRepository aisleRepository;
    private final RackRepository rackRepository;
    private final StorageLevelRepository levelRepository;
    private final BinLocationRepository binRepository;
    private final InventoryItemRepository itemRepository;

    public StorageLocationService(WarehouseZoneRepository zoneRepository,
                                  AisleRepository aisleRepository,
                                  RackRepository rackRepository,
                                  StorageLevelRepository levelRepository,
                                  BinLocationRepository binRepository,
                                  InventoryItemRepository itemRepository) {
        this.zoneRepository = zoneRepository;
        this.aisleRepository = aisleRepository;
        this.rackRepository = rackRepository;
        this.levelRepository = levelRepository;
        this.binRepository = binRepository;
        this.itemRepository = itemRepository;
    }

    public WarehouseZone saveZone(WarehouseZone zone) {
        require(zone.getWarehouse(), "Ein Bereich benötigt ein Lager.");
        return zoneRepository.save(zone);
    }

    public Aisle saveAisle(Aisle aisle) {
        require(aisle.getZone(), "Ein Gang benötigt einen Bereich.");
        return aisleRepository.save(aisle);
    }

    public Rack saveRack(Rack rack) {
        require(rack.getAisle(), "Ein Regal benötigt einen Gang.");
        return rackRepository.save(rack);
    }

    public StorageLevel saveLevel(StorageLevel level) {
        require(level.getRack(), "Eine Ebene benötigt ein Regal.");
        return levelRepository.save(level);
    }

    public BinLocation saveBin(BinLocation bin) {
        require(bin.getStorageLevel(), "Ein Lagerplatz benötigt eine Ebene.");
        if (bin.getCapacity() == null || bin.getCapacity() < 0) {
            throw badRequest("Die Lagerplatzkapazität darf nicht negativ sein.");
        }
        return binRepository.save(bin);
    }

    @Transactional
    public void moveItem(Long itemId, Long binLocationId) {
        InventoryItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> notFound("Item nicht gefunden"));
        BinLocation target = getActiveBin(binLocationId);
        if (item.getWarehouse() != null && !item.getWarehouse().getId().equals(target.getWarehouse().getId())) {
            throw badRequest("Der Lagerplatz gehört zu einem anderen Lager.");
        }
        ensureCapacity(target, item, item.getQuantityInStock() == null ? 0 : item.getQuantityInStock());
        item.setBinLocation(target);
        item.setWarehouse(target.getWarehouse());
        itemRepository.save(item);
    }

    public BinLocation getActiveBin(Long binLocationId) {
        BinLocation bin = binRepository.findById(binLocationId)
                .orElseThrow(() -> notFound("Lagerplatz nicht gefunden"));
        if (!bin.isActive()) {
            throw badRequest("Der Lagerplatz ist deaktiviert.");
        }
        return bin;
    }

    public void ensureCapacity(BinLocation bin, InventoryItem item, int addedQuantity) {
        double used = itemRepository.findByBinLocationId(bin.getId()).stream()
                .filter(existing -> item == null || !existing.getId().equals(item.getId()))
                .mapToDouble(this::spaceUsed)
                .sum();
        double capacity = bin.getCapacity() == null ? 0.0 : bin.getCapacity();
        double requested = addedQuantity * (item == null || item.getSpacePerUnit() == null
                ? 0.0 : item.getSpacePerUnit());
        if (used + requested > capacity) {
            throw badRequest("Nicht genügend Kapazität am Lagerplatz verfügbar.");
        }
    }

    private double spaceUsed(InventoryItem item) {
        int quantity = item.getQuantityInStock() == null ? 0 : item.getQuantityInStock();
        double spacePerUnit = item.getSpacePerUnit() == null ? 0.0 : item.getSpacePerUnit();
        return quantity * spacePerUnit;
    }

    private void require(Object value, String message) {
        if (value == null) {
            throw badRequest(message);
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}