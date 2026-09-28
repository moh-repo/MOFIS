package com.dilaw.mohe.ums.repository;

import com.dilaw.mohe.ums.model.UmsAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@code UMSCORE.UserActions}: what each staff account did.
 *
 * <p>{@link JpaSpecificationExecutor} is here so the reads can carry
 * {@code StaffCountrySpec.actionsAdministrableBy} — a row names its actor and
 * nothing else, so the country rule is a correlated subquery over that actor's
 * assignments rather than a column this table could be filtered on. The derived
 * finders below stay for the write-side and single-user callers; every read that
 * lists more than one account's actions goes through a specification.
 */
@Repository
public interface UmsAuditLogRepository extends JpaRepository<UmsAuditLog, Long>,
        JpaSpecificationExecutor<UmsAuditLog> {

    Page<UmsAuditLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<UmsAuditLog> findByActionOrderByCreatedAtDesc(String action, Pageable pageable);

    List<UmsAuditLog> findByUserIdAndCreatedAtBetween(Long userId, LocalDateTime start, LocalDateTime end);

    Page<UmsAuditLog> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end,
            Pageable pageable);
}
