package com.logistics.warehouse_management.repository;

import com.logistics.warehouse_management.model.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {

    List<InventoryItem> findByWarehouseId(Long warehouseId);

    List<InventoryItem> findByBinLocationId(Long binLocationId);

    boolean existsBySkuIgnoreCase(String sku);

    java.util.Optional<InventoryItem> findBySkuIgnoreCase(String sku);

    boolean existsByBarcode(String barcode);

    boolean existsBySkuIgnoreCaseAndIdNot(String sku, Long id);

    boolean existsByBarcodeAndIdNot(String barcode, Long id);
}
