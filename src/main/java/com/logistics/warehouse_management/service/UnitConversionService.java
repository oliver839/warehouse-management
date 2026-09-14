package com.logistics.warehouse_management.service;

import com.logistics.warehouse_management.model.UnitConversion;
import com.logistics.warehouse_management.model.UnitOfMeasure;
import com.logistics.warehouse_management.repository.UnitConversionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class UnitConversionService {

    private final UnitConversionRepository conversionRepository;

    public UnitConversionService(UnitConversionRepository conversionRepository) {
        this.conversionRepository = conversionRepository;
    }

    public BigDecimal convert(BigDecimal quantity, UnitOfMeasure from, UnitOfMeasure to) {
        if (quantity == null || from == null || to == null) {
            throw new IllegalArgumentException("Menge und Einheiten sind erforderlich");
        }
        if (!from.getType().equals(to.getType())) {
            throw new IllegalArgumentException("Einheiten sind nicht kompatibel");
        }
        if (from.getId().equals(to.getId())) return quantity;
        UnitConversion conversion = conversionRepository.findByFromUnitIdAndToUnitId(from.getId(), to.getId())
            .orElseThrow(() -> new IllegalArgumentException("Keine Umrechnung zwischen den Einheiten konfiguriert"));
        return roundBaseQuantity(quantity.multiply(conversion.getFactor()));
    }

    public BigDecimal roundBaseQuantity(BigDecimal quantity) {
        return quantity.setScale(6, RoundingMode.HALF_UP);
    }
}