package com.logistics.warehouse_management.repository;

import com.logistics.warehouse_management.model.ShippingOutboxEvent;
import com.logistics.warehouse_management.model.ShippingOutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ShippingOutboxEventRepository extends JpaRepository<ShippingOutboxEvent, Long> {

    List<ShippingOutboxEvent> findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            ShippingOutboxStatus status, LocalDateTime now);

    List<ShippingOutboxEvent> findTop20ByStatusAndNextAttemptAtIsNullOrderByCreatedAtAsc(
            ShippingOutboxStatus status);

    @Modifying
    @Query("update ShippingOutboxEvent e set e.status = :processing, e.version = e.version + 1, "
            + "e.processingStartedAt = CURRENT_TIMESTAMP, e.workerId = :workerId "
            + "where e.id = :id and e.status = :pending")
    int claimPending(@Param("id") Long id,
                     @Param("pending") ShippingOutboxStatus pending,
                     @Param("processing") ShippingOutboxStatus processing,
                     @Param("workerId") String workerId);

    List<ShippingOutboxEvent> findTop20ByStatusAndProcessingStartedAtLessThanEqualOrderByCreatedAtAsc(
            ShippingOutboxStatus status, LocalDateTime cutoff);

    @Modifying
    @Query("update ShippingOutboxEvent e set e.status = :pending, e.processingStartedAt = null, "
            + "e.workerId = null, e.errorMessage = :errorMessage "
            + "where e.status = :processing and e.processingStartedAt <= :cutoff")
    int resetOrphanedProcessing(@Param("processing") ShippingOutboxStatus processing,
                                @Param("pending") ShippingOutboxStatus pending,
                                @Param("cutoff") LocalDateTime cutoff,
                                @Param("errorMessage") String errorMessage);

    @Modifying
    @Query("update ShippingOutboxEvent e set e.status = :pending, e.nextAttemptAt = null, e.errorMessage = null "
            + "where e.id = :id and e.status = :failed")
    int retryFailed(@Param("id") Long id,
                    @Param("failed") ShippingOutboxStatus failed,
                    @Param("pending") ShippingOutboxStatus pending);
}