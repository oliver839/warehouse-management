package com.logistics.warehouse_management.service;

import com.logistics.warehouse_management.model.PickOrderLine;
import com.logistics.warehouse_management.model.PickScan;
import com.logistics.warehouse_management.repository.PickScanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PickScanService {

    private final PickScanRepository scanRepository;

    public PickScanService(PickScanRepository scanRepository) {
        this.scanRepository = scanRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PickScan record(PickOrderLine line, String barcode, int quantity,
                           boolean accepted, String message) {
        PickScan scan = new PickScan();
        scan.setPickOrderLine(line);
        scan.setScannedBarcode(barcode);
        scan.setQuantity(quantity);
        scan.setScannedAt(LocalDateTime.now());
        scan.setAccepted(accepted);
        scan.setMessage(message);
        scan.setScannedBy("system");
        return scanRepository.save(scan);
    }
}