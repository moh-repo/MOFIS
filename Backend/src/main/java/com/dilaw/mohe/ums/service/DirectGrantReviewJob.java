package com.dilaw.mohe.ums.service;

import com.dilaw.mohe.config.PermissionCatalog;
import com.dilaw.mohe.ums.model.UmsUserPermission;
import com.dilaw.mohe.ums.repository.UmsUserPermissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The nightly review of direct permission grants (security plan R-14).
 *
 * <p>Deactivates every exception that has lapsed (audited as
 * {@code DIRECT_GRANT_EXPIRED}, on the account that held it) and writes one
 * summary line to the log naming the live exceptions and those that lapse
 * within a week. A lapsed row is already ignored by every permission read, so
 * this is housekeeping and a record, not the enforcement.
 *
 * <p>Per-user configuration codes ({@link PermissionCatalog#isPerUserConfiguration})
 * carry no expiry and are left alone.
 */
@Component
public class DirectGrantReviewJob {

    private static final Logger log = LoggerFactory.getLogger(DirectGrantReviewJob.class);

    @Autowired private UmsUserPermissionRepository userPermissionRepository;
    @Autowired private UmsAuditLogService auditLogService;
    @Autowired private UserAuthorityCache authorityCache;

    /** Runs once a day; {@code ScheduledTenantScopeAspect} gives it system scope (UMSCORE is outside RLS anyway). */
    @Scheduled(cron = "${ums.direct-grant.review-cron:0 15 6 * * *}")
    public void reviewNightly() {
        review();
    }

    /** The same, callable from a test. Returns how many rows were deactivated. */
    @Transactional
    public int review() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime soon = now.plusDays(7);
        List<String> live = new ArrayList<>();
        List<String> expiring = new ArrayList<>();
        int lapsed = 0;
        for (UmsUserPermission up : userPermissionRepository.findAllActive()) {
            String code = up.getPermission().getPermissionCode();
            if (PermissionCatalog.isPerUserConfiguration(code)) continue;
            String who = up.getUser().getUserName() + ":" + code;
            if (up.getExpiresAt() != null && !up.getExpiresAt().isAfter(now)) {
                up.setIsActive(false);
                userPermissionRepository.save(up);
                authorityCache.evict(up.getUser().getId());
                auditLogService.log(up.getUser().getId(), "DIRECT_GRANT_EXPIRED", "USER_PERMISSION",
                        up.getUser().getId(), code, "expired " + up.getExpiresAt().toLocalDate());
                lapsed++;
                continue;
            }
            live.add(who + (up.getExpiresAt() == null ? " (no expiry)" : " until " + up.getExpiresAt().toLocalDate()));
            if (up.getExpiresAt() != null && up.getExpiresAt().isBefore(soon)) expiring.add(who);
        }
        log.info("Direct-grant review: {} live exception(s), {} lapse within 7 days, {} deactivated. Live: {}. Expiring: {}",
                live.size(), expiring.size(), lapsed, live, expiring);
        return lapsed;
    }
}
