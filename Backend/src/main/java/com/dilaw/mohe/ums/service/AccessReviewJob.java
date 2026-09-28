package com.dilaw.mohe.ums.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Opens the quarter's access reviews and flags the overdue ones (S-04).
 *
 * <p>On the first day of each quarter (05:00) one review per attaché with
 * active staff and one for the ministry are opened; nightly (04:40) every
 * undecided item past its due date is flagged {@code ACCESS_REVIEW_OVERDUE}
 * and, when {@code ums.access-review.disable-unattested} is on, the account is
 * set INACTIVE. {@code ScheduledTenantScopeAspect} gives both runs system scope.
 */
@Component
public class AccessReviewJob {

    private static final Logger log = LoggerFactory.getLogger(AccessReviewJob.class);

    private final AccessReviewService service;
    private final boolean enabled;

    public AccessReviewJob(AccessReviewService service, @Value("${ums.access-review.enabled:true}") boolean enabled) {
        this.service = service;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${ums.access-review.open-cron:0 0 5 1 1,4,7,10 *}")
    public void openQuarter() {
        if (!enabled) return;
        String period = AccessReviewService.periodOf(LocalDate.now());
        int n = service.open(period, null).size();
        log.info("Access review {}: {} review(s) opened", period, n);
    }

    @Scheduled(cron = "${ums.access-review.overdue-cron:0 40 4 * * *}")
    public void nightly() {
        if (!enabled) return;
        AccessReviewService.OverdueOutcome o = service.flagOverdue(LocalDateTime.now());
        if (o.flagged() > 0) log.warn("Access review: {} account(s) not attested by the due date, {} disabled", o.flagged(), o.disabled());
    }
}
