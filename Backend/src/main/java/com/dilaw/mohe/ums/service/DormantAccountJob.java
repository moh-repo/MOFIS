package com.dilaw.mohe.ums.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * The dormant-account rule (security plan S-05).
 *
 * <p>Nightly: an active account with no sign-in for {@code ums.dormant.days}
 * (90) is set INACTIVE with the leaver cleanup (sessions ended, direct grants
 * lapsed), audited {@code ACCOUNT_DORMANT}; one past {@code ums.dormant.warn-days}
 * (75) gets one {@code ACCOUNT_DORMANT_WARNING} audit row per idle period so
 * the administrator's list shows who is about to go. An account that has
 * never signed in counts from its creation. The super admin is exempt: the
 * break-glass account must stay usable. Attaché rotation is the normal case
 * here, and a rotated officer's account going quiet is exactly the one that
 * should not keep its country.
 */
@Component
public class DormantAccountJob {

    private static final Logger log = LoggerFactory.getLogger(DormantAccountJob.class);

    private final JdbcTemplate jdbc;
    private final UmsUserService userService;
    private final UmsAuditLogService auditLogService;
    private final boolean enabled;
    private final int days;
    private final int warnDays;

    public DormantAccountJob(JdbcTemplate jdbc, UmsUserService userService, UmsAuditLogService auditLogService,
                             @Value("${ums.dormant.enabled:true}") boolean enabled,
                             @Value("${ums.dormant.days:90}") int days,
                             @Value("${ums.dormant.warn-days:75}") int warnDays) {
        this.jdbc = jdbc;
        this.userService = userService;
        this.auditLogService = auditLogService;
        this.enabled = enabled;
        this.days = Math.max(7, days);
        this.warnDays = Math.min(this.days - 1, Math.max(1, warnDays));
    }

    public record Outcome(int warned, int disabled) {}

    @Scheduled(cron = "${ums.dormant.cron:0 20 4 * * *}")
    public void nightly() {
        if (!enabled) {
            log.info("Dormant-account rule is off (ums.dormant.enabled=false)");
            return;
        }
        run(LocalDateTime.now());
    }

    /** The same, callable from a test with a fixed clock. */
    public Outcome run(LocalDateTime now) {
        int warned = 0, disabled = 0;
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT Id, Username, COALESCE(LastLoginAt, CreatedAt) AS Since
                FROM UMSCORE.Users
                WHERE IsDeleted = 0 AND IsSuperAdmin = 0 AND Status = N'ACTIVE'
                  AND COALESCE(LastLoginAt, CreatedAt) < ?
                """, Timestamp.valueOf(now.minusDays(warnDays)));
        for (Map<String, Object> row : rows) {
            long userId = ((Number) row.get("Id")).longValue();
            LocalDateTime since = ((Timestamp) row.get("Since")).toLocalDateTime();
            long idle = Duration.between(since, now).toDays();
            if (idle >= days) {
                if (userService.disableDormant(userId, idle)) disabled++;
            } else if (!warnedSince(userId, since)) {
                auditLogService.log(userId, "ACCOUNT_DORMANT_WARNING", "USER", userId, null,
                        "no sign-in for " + idle + " days; disabled at " + days);
                warned++;
            }
        }
        log.info("Dormant accounts: {} warned, {} disabled ({} days, warning at {})", warned, disabled, days, warnDays);
        return new Outcome(warned, disabled);
    }

    private boolean warnedSince(long userId, LocalDateTime since) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM UMSCORE.UserActions WHERE UserId = ? AND [Action] = N'ACCOUNT_DORMANT_WARNING' AND CreatedAt > ?",
                Integer.class, userId, Timestamp.valueOf(since));
        return n != null && n > 0;
    }
}
