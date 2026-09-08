package com.logistics.warehouse_management.controller;

import com.logistics.warehouse_management.model.Aisle;
import com.logistics.warehouse_management.model.BinLocation;
import com.logistics.warehouse_management.model.Rack;
import com.logistics.warehouse_management.model.StorageLevel;
import com.logistics.warehouse_management.model.WarehouseZone;
import com.logistics.warehouse_management.repository.AisleRepository;
import com.logistics.warehouse_management.repository.BinLocationRepository;
import com.logistics.warehouse_management.repository.RackRepository;
import com.logistics.warehouse_management.repository.StorageLevelRepository;
import com.logistics.warehouse_management.repository.WarehouseZoneRepository;
import com.logistics.warehouse_management.service.StorageLocationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/storage-locations")
public class StorageLocationController {

    private final WarehouseZoneRepository zoneRepository;
    private final AisleRepository aisleRepository;
    private final RackRepository rackRepository;
    private final StorageLevelRepository levelRepository;
    private final BinLocationRepository binRepository;
    private final StorageLocationService service;

    public StorageLocationController(WarehouseZoneRepository zoneRepository,
                                     AisleRepository aisleRepository,
                                     RackRepository rackRepository,
                                     StorageLevelRepository levelRepository,
                                     BinLocationRepository binRepository,
                                     StorageLocationService service) {
        this.zoneRepository = zoneRepository;
        this.aisleRepository = aisleRepository;
        this.rackRepository = rackRepository;
        this.levelRepository = levelRepository;
        this.binRepository = binRepository;
        this.service = service;
    }

    @GetMapping("/zones")
    public List<WarehouseZone> getZones() { return zoneRepository.findAll(); }

    @PostMapping("/zones")
    public WarehouseZone createZone(@RequestBody WarehouseZone zone) { return service.saveZone(zone); }

    @PutMapping("/zones/{id}")
    public WarehouseZone updateZone(@PathVariable Long id, @RequestBody WarehouseZone zone) {
        zone.setId(id);
        return service.saveZone(zone);
    }

    @DeleteMapping("/zones/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteZone(@PathVariable Long id) { zoneRepository.deleteById(id); }

    @GetMapping("/aisles")
    public List<Aisle> getAisles() { return aisleRepository.findAll(); }

    @PostMapping("/aisles")
    public Aisle createAisle(@RequestBody Aisle aisle) { return service.saveAisle(aisle); }

    @PutMapping("/aisles/{id}")
    public Aisle updateAisle(@PathVariable Long id, @RequestBody Aisle aisle) {
        aisle.setId(id);
        return service.saveAisle(aisle);
    }

    @DeleteMapping("/aisles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAisle(@PathVariable Long id) { aisleRepository.deleteById(id); }

    @GetMapping("/racks")
    public List<Rack> getRacks() { return rackRepository.findAll(); }

    @PostMapping("/racks")
    public Rack createRack(@RequestBody Rack rack) { return service.saveRack(rack); }

    @PutMapping("/racks/{id}")
    public Rack updateRack(@PathVariable Long id, @RequestBody Rack rack) {
        rack.setId(id);
        return service.saveRack(rack);
    }

    @DeleteMapping("/racks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRack(@PathVariable Long id) { rackRepository.deleteById(id); }

    @GetMapping("/levels")
    public List<StorageLevel> getLevels() { return levelRepository.findAll(); }

    @PostMapping("/levels")
    public StorageLevel createLevel(@RequestBody StorageLevel level) { return service.saveLevel(level); }

    @PutMapping("/levels/{id}")
    public StorageLevel updateLevel(@PathVariable Long id, @RequestBody StorageLevel level) {
        level.setId(id);
        return service.saveLevel(level);
    }

    @DeleteMapping("/levels/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLevel(@PathVariable Long id) { levelRepository.deleteById(id); }

    @GetMapping("/bins")
    public List<BinLocation> getBins() { return binRepository.findAll(); }

    @PostMapping("/bins")
    public BinLocation createBin(@RequestBody BinLocation bin) { return service.saveBin(bin); }

    @PutMapping("/bins/{id}")
    public BinLocation updateBin(@PathVariable Long id, @RequestBody BinLocation bin) {
        bin.setId(id);
        return service.saveBin(bin);
    }

    @DeleteMapping("/bins/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBin(@PathVariable Long id) { binRepository.deleteById(id); }
}