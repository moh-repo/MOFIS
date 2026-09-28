package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * {@code MOHE_ALERTS.alerts} — the system notices. Status, type and priority
 * are codes of {@code LU_NOTICE_STATUS}, {@code LU_ALERT_TYPE} and
 * {@code LU_ALERT_SEVERITY}; the service binds the constants, nothing here
 * names a code.
 */
@Repository
public interface AlertRepository extends JpaRepository<Alert, Integer> {

    List<Alert> findTop10ByOrderByCreatedDateDesc();

    /** The notices a source raised that are still open (H2). */
    List<Alert> findBySourceRefAndAlertStatus(String sourceRef, String alertStatus);

    List<Alert> findByAlertStatusOrderByCreatedDateDesc(String status);

    List<Alert> findByAlertTypeOrderByCreatedDateDesc(String alertType);

    long countByAlertPriority(String alertPriority);

    /**
     * Notices created in {@code [start, end)}, newest first. Replaces two native
     * queries that applied {@code CONVERT(date, created_date)} to the column —
     * a function on the filtered column forces a full scan regardless of any
     * index; a half-open range is SARGable. The service computes the day bounds
     * in the application timezone (the old queries used the DB server's date).
     */
    @Query("SELECT a FROM Alert a WHERE a.createdDate >= :start AND a.createdDate < :end ORDER BY a.createdDate DESC")
    List<Alert> findByCreatedDateInRange(@Param("start") java.time.Instant start,
                                         @Param("end") java.time.Instant end);

    /** Notices in {@code status} that have not expired. */
    @Query("SELECT a FROM Alert a WHERE a.alertStatus = :status " +
           "AND (a.expiresDate IS NULL OR a.expiresDate > CURRENT_TIMESTAMP) " +
           "ORDER BY a.createdDate DESC")
    List<Alert> findUnexpiredByStatus(@Param("status") String status);

    long countByAlertStatus(String status);

    // Read state is per account since V144 (live QA N10): AlertReadRepository.
}
