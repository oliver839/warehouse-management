package com.logistics.warehouse_management.repository;

import com.logistics.warehouse_management.model.UnitConversion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UnitConversionRepository extends JpaRepository<UnitConversion, Long> {

    Optional<UnitConversion> findByFromUnitIdAndToUnitId(Long fromUnitId, Long toUnitId);
}