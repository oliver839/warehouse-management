package com.logistics.warehouse_management.repository;

import com.logistics.warehouse_management.model.StockPosition;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockPositionRepository extends JpaRepository<StockPosition, Long> {

    List<StockPosition> findByInventoryItemId(Long inventoryItemId);

    List<StockPosition> findByBinLocationId(Long binLocationId);

    Optional<StockPosition> findByInventoryItemIdAndBinLocationId(Long inventoryItemId, Long binLocationId);

    java.util.List<StockPosition> findByStatus(com.logistics.warehouse_management.model.StockStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from StockPosition p where p.inventoryItem.id = :itemId order by p.id asc")
    List<StockPosition> findByInventoryItemIdForUpdate(@Param("itemId") Long itemId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from StockPosition p where p.id = :id")
    Optional<StockPosition> findByIdForUpdate(@Param("id") Long id);
}