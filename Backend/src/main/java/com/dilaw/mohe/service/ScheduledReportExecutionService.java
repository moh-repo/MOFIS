package com.dilaw.mohe.service;

import com.dilaw.mohe.config.tenant.TenantContext;
import com.dilaw.mohe.model.CustomFilterReport;
import com.dilaw.mohe.model.ScheduledReportEntity;
import com.dilaw.mohe.notification.model.NotificationSettings;
import com.dilaw.mohe.notification.repository.NotificationSettingsRepository;
import com.dilaw.mohe.notification.service.EmailService;
import com.dilaw.mohe.report.excel.ExcelExportService;
import com.dilaw.mohe.repository.CustomFilterReportRepository;
import com.dilaw.mohe.repository.ScheduledReportEntityRepository;
import com.dilaw.mohe.ums.model.UmsUser;
import com.dilaw.mohe.ums.repository.UmsUserRepository;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.util.SecurityContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Manages SCHEDULED_REPORT CRUD and runs due schedules every 15 minutes. */
@Service
public class ScheduledReportExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledReportExecutionService.class);

    @Autowired
    private ScheduledReportEntityRepository repository;

    @Autowired
    private CustomFilterReportRepository customFilterReportRepository;

    @Autowired
    private ExcelExportService excelExportService;

    @Autowired
    private NotificationSettingsRepository notificationSettingsRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private UmsUserRepository umsUserRepository;

    @Autowired
    private CountryScopeService countryScopeService;

    @Autowired
    private ScheduledReportClaimService claimService;

    @Autowired
    private com.dilaw.mohe.security.field.ReportFieldMask reportFieldMask;

    /**
     * Report types this scheduler knows how to run.
     *
     * <p>Checked when a schedule is saved as well as when it runs. It used to be
     * checked only at run time, so {@code POST /manage} accepted any string and the
     * schedule was created, enabled, and discovered to be unrunnable a month later in
     * the log — which is what {@code SCHEDULED_REPORT} 220 ({@code FINANCIAL_SUMMARY})
     * was. A schedule that can never produce anything is refused at the door instead.
     */
    private static final Set<String> RUNNABLE_REPORT_TYPES = Set.of("STUDENT_LIST");

    /** The default when a schedule names no type, and the only entry above. */
    private static final String REPORT_TYPE_STUDENT_LIST = "STUDENT_LIST";

    /** The type as the runner reads it: trimmed, upper case, defaulted. */
    private static String normalisedReportType(ScheduledReportEntity r) {
        return r.getReportType() == null ? REPORT_TYPE_STUDENT_LIST
                : r.getReportType().trim().toUpperCase();
    }

    /**
     * Refuses a schedule the runner could never execute.
     *
     * @throws ResponseStatusException 400, so the caller is told at once and by the
     *         same route as every other bad request on this controller
     */
    private static void requireRunnableReportType(ScheduledReportEntity entity) {
        String reportType = normalisedReportType(entity);
        if (!RUNNABLE_REPORT_TYPES.contains(reportType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Report type '" + entity.getReportType() + "' cannot be scheduled: the scheduler"
                    + " can run " + String.join(", ", RUNNABLE_REPORT_TYPES) + ".");
        }
    }

    // ── CRUD ────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ScheduledReportEntity> getAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public ScheduledReportEntity getById(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Scheduled report not found: " + id));
    }

    @Transactional
    public ScheduledReportEntity create(ScheduledReportEntity entity) {
        requireRunnableReportType(entity);
        entity.setScheduleId(null);
        entity.setCreatedBy(SecurityContextHelper.currentUsername());
        if (entity.getNextRunDate() == null) {
            entity.setNextRunDate(computeNextRun(entity.getFrequency()));
        }
        return repository.save(entity);
    }

    @Transactional
    public ScheduledReportEntity update(Integer id, ScheduledReportEntity entity) {
        requireRunnableReportType(entity);
        ScheduledReportEntity existing = getById(id);
        entity.setScheduleId(id);
        entity.setCreatedDate(existing.getCreatedDate());
        entity.setCreatedBy(existing.getCreatedBy());
        entity.setModifiedBy(SecurityContextHelper.currentUsername());
        return repository.save(entity);
    }

    @Transactional
    public void delete(Integer id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Scheduled report not found: " + id);
        }
        repository.deleteById(id);
    }

    @Transactional
    public ScheduledReportEntity toggleEnabled(Integer id) {
        ScheduledReportEntity r = getById(id);
        r.setIsEnabled(!r.getIsEnabled());
        r.setModifiedBy(SecurityContextHelper.currentUsername());
        return repository.save(r);
    }

    // ── Scheduler ───────────────────────────────────────────────────────────────

    /**
     * Runs every 15 minutes; executes any schedule whose next_run_date has passed.
     *
     * <p>Deliberately <em>not</em> transactional. The old version did the whole loop in
     * one transaction: {@code RUNNING} was written inside it, so a second instance (or
     * an overlapping tick) still saw the row as due and sent the report again; and the
     * e-mail went out inside it, so a rollback after the send left the row due and the
     * next tick sent it once more. Now each schedule is claimed with a conditional
     * update in its own committed transaction ({@link ScheduledReportClaimService#claim}),
     * the report runs and is sent with no transaction open, and the outcome is written
     * in another committed transaction. A crash between claim and finish leaves the row
     * {@code RUNNING}; the claim treats that as reclaimable after two hours.
     */
    @Scheduled(fixedDelay = 15 * 60 * 1000)
    public void executeDueReports() {
        List<ScheduledReportEntity> due = repository.findDueReports(Instant.now());
        if (due.isEmpty()) return;

        log.info("ScheduledReportExecutionService: {} due report(s)", due.size());
        for (ScheduledReportEntity r : due) {
            if (!claimService.claim(r.getScheduleId(), Instant.now())) {
                log.info("Report '{}' skipped: claimed by another run or no longer due", r.getScheduleName());
                continue;
            }
            try {
                int count = executeReport(r);
                Instant nextRun = computeNextRun(r.getFrequency());
                claimService.finish(r.getScheduleId(), true, count, nextRun);
                log.info("Report '{}' executed: {} record(s). Next run: {}", r.getScheduleName(), count, nextRun);
            } catch (ScheduleConfigurationException ex) {
                /*
                 * Broken rather than unlucky: the next tick would fail identically. The
                 * outcome is still FAILED, so the schedule shows as broken on the screen,
                 * but the next run advances by its own frequency instead of staying in the
                 * past — otherwise this logs once every fifteen minutes for ever. Message
                 * only: the stack trace of a configuration error says nothing the sentence
                 * does not.
                 */
                Instant nextRun = computeNextRun(r.getFrequency());
                log.warn("Report '{}' cannot run and will not until it is corrected: {} Next attempt: {}",
                        r.getScheduleName(), ex.getMessage(), nextRun);
                claimService.finish(r.getScheduleId(), false, null, nextRun);
            } catch (Exception ex) {
                // Possibly transient — SMTP, the database — so the next tick tries again.
                log.error("Report '{}' failed: {}", r.getScheduleName(), ex.getMessage(), ex);
                claimService.finish(r.getScheduleId(), false, null, null);
            }
        }
    }

    /**
     * Runs one schedule and emails the result.
     *
     * <p><b>Country scope.</b> This used to call
     * {@code findAllFiltered(false, List.of(-1))} — the sentinel that disables
     * country filtering entirely — because there is no {@code SecurityContext}
     * in a {@code @Scheduled} thread. The effect was that a schedule created by
     * a user restricted to one country emailed them the entire ministry's
     * student list every week. The owner's scope is now resolved from
     * {@code CREATED_BY} through {@link CountryScopeService#getScopeForUser},
     * which does not need a SecurityContext. A schedule whose owner can no
     * longer be resolved, or who has no scope, is refused rather than widened.
     *
     * <p><b>Report type.</b> The configured {@code REPORT_TYPE} used to be
     * ignored: every schedule ran the same all-students query with the same ten
     * columns, so two schedules configured for different reports produced
     * identical spreadsheets. An unknown type now fails the run loudly instead
     * of silently sending the student list.
     */
    private int executeReport(ScheduledReportEntity r) throws Exception {
        UmsUser owner = resolveOwner(r);
        List<Integer> countryIds = resolveOwnerCountryIds(r, owner);

        String reportType = normalisedReportType(r);
        if (!RUNNABLE_REPORT_TYPES.contains(reportType)) {
            throw new ScheduleConfigurationException("Scheduled report '" + r.getScheduleName()
                    + "' is configured for report type '" + r.getReportType()
                    + "', which the scheduler cannot run. Supported types: "
                    + String.join(", ", RUNNABLE_REPORT_TYPES));
        }

        // Narrowed to the owner's countries at the database layer too, not just in
        // the query predicate. ScheduledTenantScopeAspect elevates this whole job
        // to system scope so it can find schedules across every country; without
        // this the report itself would also run elevated, and the RLS policy that
        // exists to catch a forgotten predicate would have nothing to catch.
        List<CustomFilterReport> data = TenantContext.runAs(
                TenantContext.Scope.of(false, countryIds),
                () -> customFilterReportRepository.findAllFiltered(true, countryIds));

        String[] headers = {"Student Name", "Passport Number", "Country", "University", "Major",
                            "Degree Level", "Study Status", "Study Type", "Enrollment Year", "Financial Amount"};
        String[] fields  = {"studentName", "passportNumber", "countryName", "universityName", "majorName",
                            "degreeLevelName", "studyStatusName", "studyTypeName", "enrollmentYear", "financialAmount"};
        // Masked by the owner's own permissions (W5-03). A @Scheduled thread has no
        // security context, so the signed-in caller here is nobody and holds nothing;
        // left to read the context, this spreadsheet would arrive with every
        // sensitive column blank. The owner is who the report is for and whose
        // country scope it already runs under, so the owner is who it is masked for.
        byte[] xlsxBytes = excelExportService.buildWorkbook(r.getScheduleName(), headers, fields, data,
                reportFieldMask.viewerFor(owner.getId()));

        if (r.getRecipientEmail() != null && !r.getRecipientEmail().isBlank()) {
            Optional<NotificationSettings> cfgOpt = notificationSettingsRepository.findFirstByCountryIdIsNull();
            if (cfgOpt.isPresent()) {
                String today = LocalDate.now().toString();
                String filename = "report-" + today + ".xlsx";
                String subject = r.getScheduleName() + " — " + today;
                String body = "<p>Please find attached the scheduled report: <strong>"
                        + r.getScheduleName() + "</strong></p>"
                        + "<p>Total records: " + data.size() + "</p>";
                String err = emailService.sendWithAttachment(
                        cfgOpt.get(), r.getRecipientEmail(), subject, body, xlsxBytes, filename);
                if (err != null) {
                    log.warn("Report '{}' email failed: {}", r.getScheduleName(), err);
                } else {
                    log.info("Report '{}' emailed to {} ({} records)", r.getScheduleName(), r.getRecipientEmail(), data.size());
                }
            } else {
                log.warn("Report '{}': no global SMTP settings found, skipping email", r.getScheduleName());
            }
        }
        return data.size();
    }

    /**
     * The account a schedule runs as: its creator.
     *
     * <p>Both the country scope and the field mask (W5-03) are taken from this
     * user, so a schedule is answered with exactly what its owner could have
     * seen had they run the report themselves.
     *
     * @throws ScheduleConfigurationException if the owner cannot be resolved — a
     *         schedule is never silently widened when its owner is unknown.
     */
    private UmsUser resolveOwner(ScheduledReportEntity r) {
        String owner = r.getCreatedBy();
        if (owner == null || owner.isBlank()) {
            throw new ScheduleConfigurationException("Scheduled report '" + r.getScheduleName()
                    + "' has no CREATED_BY, so its country scope cannot be established.");
        }
        return umsUserRepository.findByUserNameAndIsDeletedFalse(owner)
                .orElseThrow(() -> new ScheduleConfigurationException("Scheduled report '" + r.getScheduleName()
                        + "' is owned by '" + owner + "', who no longer exists."));
    }

    /**
     * The countries a schedule may read, from its owner.
     *
     * @throws ScheduleConfigurationException if the owner has no country scope —
     *         a schedule is never silently widened to the whole ministry.
     */
    private List<Integer> resolveOwnerCountryIds(ScheduledReportEntity r, UmsUser user) {
        CountryScopeService.CountryScope scope = countryScopeService.getScopeForUser(user.getId());
        List<Integer> countryIds = scope.getAllowedCountryIds();
        if (countryIds == null || countryIds.isEmpty()) {
            throw new ScheduleConfigurationException("Scheduled report '" + r.getScheduleName()
                    + "' is owned by '" + user.getUserName() + "', who has no country scope.");
        }
        return countryIds;
    }

    /**
     * Next run time.
     *
     * <p>MONTHLY used to add a flat 30 days, so a schedule set for the 31st of
     * January drifted a little further from month start on every run. Calendar
     * units keep the day of month stable.
     */
    private static Instant computeNextRun(String frequency) {
        ZonedDateTime now = ZonedDateTime.now();
        if (frequency == null) return now.plusWeeks(1).toInstant();
        return switch (frequency.toUpperCase()) {
            case "DAILY"     -> now.plusDays(1).toInstant();
            case "MONTHLY"   -> now.plusMonths(1).toInstant();
            case "QUARTERLY" -> now.plusMonths(3).toInstant();
            case "YEARLY"    -> now.plusYears(1).toInstant();
            default          -> now.plusWeeks(1).toInstant();  // WEEKLY
        };
    }
}
