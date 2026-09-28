package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.AlertRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/** Who has read which alert (V144; live QA N10). */
@Repository
public interface AlertReadRepository extends JpaRepository<AlertRead, AlertRead.Key> {

    boolean existsByAlertIdAndUserId(Integer alertId, Long userId);

    /** Of {@code alertIds}, the ones {@code userId} has read. */
    @Query("SELECT r.alertId FROM AlertRead r WHERE r.userId = :userId AND r.alertId IN :alertIds")
    List<Integer> findReadAmong(@Param("userId") Long userId, @Param("alertIds") Collection<Integer> alertIds);

    /** The alerts {@code userId} has not read. */
    @Query("SELECT COUNT(a) FROM Alert a WHERE NOT EXISTS "
            + "(SELECT r FROM AlertRead r WHERE r.alertId = a.alertId AND r.userId = :userId)")
    long countUnreadFor(@Param("userId") Long userId);

    /** Marks every alert {@code userId} has not read as read by them; returns how many. */
    @Modifying
    @Query(value = "INSERT INTO MOHE_ALERTS.alert_reads (alert_id, user_id, read_at) "
            + "SELECT a.alert_id, :userId, SYSUTCDATETIME() FROM MOHE_ALERTS.alerts a "
            + "WHERE NOT EXISTS (SELECT 1 FROM MOHE_ALERTS.alert_reads r "
            + "WHERE r.alert_id = a.alert_id AND r.user_id = :userId)", nativeQuery = true)
    int markAllReadFor(@Param("userId") Long userId);
}
