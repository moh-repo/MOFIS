package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.codes.PortalAccountStatus;
import com.dilaw.mohe.config.tenant.TenantContext;
import com.dilaw.mohe.security.SecurityEventLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * The dormant-account rule for the student portal (security plan S-11, the
 * portal half of S-05): an active portal account that has not signed in for
 * {@code mohe.student-portal.dormant.days} (a year by default; a never-used
 * account counts from its creation) is set INACTIVE. The attaché reactivates
 * it from the portal accounts screen when the student turns up.
 */
@Component
public class PortalDormantAccountJob {

    private static final Logger log = LoggerFactory.getLogger(PortalDormantAccountJob.class);

    private final JdbcTemplate jdbc;
    private final SecurityEventLog securityEventLog;
    private final boolean enabled;
    private final int days;

    public PortalDormantAccountJob(JdbcTemplate jdbc, SecurityEventLog securityEventLog,
                                   @Value("${mohe.student-portal.dormant.enabled:true}") boolean enabled,
                                   @Value("${mohe.student-portal.dormant.days:365}") int days) {
        this.jdbc = jdbc;
        this.securityEventLog = securityEventLog;
        this.enabled = enabled;
        this.days = Math.max(1, days);
    }

    @Scheduled(cron = "${mohe.student-portal.dormant.cron:0 20 3 * * *}")
    public void nightly() {
        if (!enabled) return;
        try {
            // runAsSystem: the rule applies to every attaché's portal accounts; the job runs on
            // nobody's behalf and only sets a status, reported on the security log.
            TenantContext.runAsSystem(() -> run(LocalDateTime.now()));
        } catch (Exception e) {
            log.error("Portal dormant-account run failed: {}", e.getMessage(), e);
        }
    }

    /** Disables the dormant accounts; returns how many. */
    @Transactional
    public int run(LocalDateTime now) {
        LocalDateTime cutoff = now.minusDays(days);
        int disabled = jdbc.update("""
                UPDATE STUDENTCORE.student_users
                   SET status = ?, updated_at = SYSUTCDATETIME()
                 WHERE status = ? AND is_deleted = 0
                   AND COALESCE(last_login_at, created_at) < ?
                """, PortalAccountStatus.INACTIVE, PortalAccountStatus.ACTIVE, cutoff);
        if (disabled > 0) {
            securityEventLog.emit("PORTAL_ACCOUNTS_DORMANT", null, "system", "StudentUser", null,
                    disabled + " portal accounts set INACTIVE: no sign-in since " + cutoff.toLocalDate(), null);
            log.info("Portal dormant-account rule: {} accounts set INACTIVE (no sign-in since {})", disabled, cutoff.toLocalDate());
        }
        return disabled;
    }
}
