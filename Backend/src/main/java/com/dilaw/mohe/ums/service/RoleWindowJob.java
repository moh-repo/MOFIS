package com.dilaw.mohe.ums.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Opens and closes role windows (security plan R-21).
 *
 * <p>A role assigned for a period, or delegated for a colleague's absence, is a
 * {@code UserRoles} row with {@code ValidFrom}/{@code ValidTo}. The permission
 * reads only look at {@code IsActive}; this job flips it as the window opens
 * and closes, once a night, and audits each flip on the account
 * ({@code ROLE_WINDOW_OPENED}, {@code ROLE_WINDOW_CLOSED}). UMSCORE is outside
 * RLS; {@code ScheduledTenantScopeAspect} gives the run system scope anyway.
 */
@Component
public class RoleWindowJob {

    private static final Logger log = LoggerFactory.getLogger(RoleWindowJob.class);

    @Autowired private UmsUserService userService;

    @Scheduled(cron = "${ums.role-window.cron:0 5 0 * * *}")
    public void nightly() {
        run(LocalDateTime.now());
    }

    /** The same, callable from a test. */
    public void run(LocalDateTime now) {
        int closed = userService.closeExpiredRoleWindows(now);
        int opened = userService.openDueRoleWindows(now);
        log.info("Role windows: {} closed, {} opened as of {}", closed, opened, now);
    }
}
