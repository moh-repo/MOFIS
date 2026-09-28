package com.dilaw.mohe.scope;

import com.dilaw.mohe.alerteventcases.service.AlertEventCaseService;
import com.dilaw.mohe.model.MoheQuarter;
import com.dilaw.mohe.notification.service.NotificationService;
import com.dilaw.mohe.service.MoheQuarterService;
import com.dilaw.mohe.service.StudyUniversityFeeArchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The remaining four of the ten pages.
 *
 * <p>Grouped because each needs only a few assertions, and because they differ in
 * kind: the university fee archive had no scoping at all, notification history had
 * scoping that filtered on an empty table, alert cases were already correct, and
 * quarters are shared by design with only the money against them scoped.
 *
 * <p>Alert cases are here to be <em>pinned</em> rather than fixed. They were the
 * one page of the ten already scoped end to end — list, summary, detail and all
 * three mutations — and a test is what keeps that true.
 *
 * <p>Read-only.
 */
@Tag("integration")
@SpringBootTest
class RemainingPagesScopeTest {

    @Autowired private StudyUniversityFeeArchService feeArchService;
    @Autowired private NotificationService notificationService;
    @Autowired private AlertEventCaseService alertEventCaseService;
    @Autowired private MoheQuarterService moheQuarterService;
    @Autowired private JdbcTemplate jdbc;

    // ── /studyuniversityfeearch ──────────────────────────────────────────────

    @Test
    @DisplayName("the archived fees follow the same rule as the live ones")
    void feeArchiveIsScoped() {
        assumeTrue(!AsUser.call(AsUser.SUPERADMIN, () -> feeArchService.getAll()).isEmpty(),
                "no archived fees");

        assertThat(AsUser.call(AsUser.JO_ADMIN, () -> feeArchService.getAll()))
                .as("""
                    StudyUniversityFeeService has been scoped for a while and this archive twin \
                    never was, so the same data was readable one page across.""")
                .isEmpty();

        assertThat(AsUser.call(AsUser.ADMIN, () -> feeArchService.getAll()))
                .as("positive control: an officer holding every country still reads them")
                .isNotEmpty();
    }

    @Test
    @DisplayName("an archived fee cannot be fetched, edited or deleted from another attaché")
    void feeArchiveByIdIsScoped() {
        Integer studentId = jdbc.queryForObject(
                "SELECT TOP 1 STUDENT_ID FROM MOHECORE.STUDY_UNIVERSITY_FEE_ARCH WHERE STUDY_COUNTRY_IN_ID = 1",
                Integer.class);
        assumeTrue(studentId != null, "no Egyptian archived fees");

        assertThat(AsUser.call(AsUser.SUPERADMIN, () -> feeArchService.getById(studentId))).isNotNull();

        assertThatThrownBy(() -> AsUser.call(AsUser.JO_ADMIN, () -> feeArchService.getById(studentId)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");

        assertThatThrownBy(() -> AsUser.run(AsUser.JO_ADMIN, () -> feeArchService.delete(studentId)))
                .as("delete goes through the same gate as the read, so it cannot reach further")
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM MOHECORE.STUDY_UNIVERSITY_FEE_ARCH WHERE STUDENT_ID = ?",
                Integer.class, studentId))
                .as("and the refused delete really did not delete anything")
                .isPositive();
    }

    // ── /notifications/history ───────────────────────────────────────────────

    @Test
    @DisplayName("a message sent to everyone stays visible to everyone")
    void notificationHistoryShowsMinistryWideSends() {
        Integer allScope = jdbc.queryForObject("""
                SELECT COUNT(*) FROM MOHECORE.NOTIFICATION_LOG nl
                WHERE nl.SCOPE = N'ALL'
                  AND NOT EXISTS (SELECT 1 FROM MOHECORE.NOTIFICATION_LOG_COUNTRY c WHERE c.LOG_ID = nl.ID)
                """, Integer.class);
        assumeTrue(allScope != null && allScope > 0, "no ministry-wide sends without country rows");

        long jordanSees = AsUser.call(AsUser.JO_ADMIN,
                () -> notificationService.getHistory(0, 50, null).getTotalElements());

        assertThat(jordanSees)
                .as("""
                    NOTIFICATION_LOG_COUNTRY is empty against eight logs, so the EXISTS filter \
                    alone hid all eight from every country user — the rule was right and the data \
                    behind it had never been written. A SCOPE = 'ALL' send went to everyone, so \
                    everyone may see that it was sent.""")
                .isEqualTo(allScope.longValue());
    }

    @Test
    @DisplayName("a message sent to a group or one person is not shown to every attaché")
    void notificationHistoryHidesTargetedSends() {
        long ministrySees = AsUser.call(AsUser.SUPERADMIN,
                () -> notificationService.getHistory(0, 50, null).getTotalElements());
        long jordanSees = AsUser.call(AsUser.JO_ADMIN,
                () -> notificationService.getHistory(0, 50, null).getTotalElements());

        assumeTrue(ministrySees > 0, "no notification history");
        assertThat(jordanSees)
                .as("""
                    The GROUP and ONE sends record no recipient country. Showing them to every \
                    attaché because we lost track of which is the wrong way to resolve it.""")
                .isLessThan(ministrySees);
    }

    // ── /alert-events/cases — already correct, pinned here ────────────────────

    @Test
    @DisplayName("alert cases were already scoped, and stay that way")
    void alertCasesRemainScoped() {
        long ministryOpen = AsUser.call(AsUser.SUPERADMIN,
                () -> alertEventCaseService.getSummary().getTotalOpen()
                    + alertEventCaseService.getSummary().getTotalResolved());
        assumeTrue(ministryOpen > 0, "no alert cases");

        assertThat(AsUser.call(AsUser.JO_ADMIN, () -> alertEventCaseService.getSummary().getTotalOpen()))
                .as("every case in this database belongs to Egypt")
                .isZero();

        Integer egyptianCase = jdbc.queryForObject(
                "SELECT TOP 1 CASE_ID FROM ALERTEVENTCASES.ALERT_EVENT_CASE WHERE STUDENT_COUNTRY_ID = 1",
                Integer.class);
        assumeTrue(egyptianCase != null, "no Egyptian cases");

        assertThatThrownBy(() -> AsUser.call(AsUser.JO_ADMIN, () -> alertEventCaseService.getById(egyptianCase)))
                .as("the detail path has always checked, and this is what keeps it checking")
                .isInstanceOf(Exception.class);
    }

    // ── /operations/mohe-quarter ─────────────────────────────────────────────

    @Test
    @DisplayName("quarters are shared, but the money against them is not")
    void quartersAreSharedAndFiguresAreScoped() {
        List<MoheQuarter> ministry = AsUser.call(AsUser.SUPERADMIN, () -> moheQuarterService.getAll());
        List<MoheQuarter> jordan = AsUser.call(AsUser.JO_ADMIN, () -> moheQuarterService.getAll());
        assumeTrue(!ministry.isEmpty(), "no quarters");

        assertThat(jordan)
                .as("""
                    A quarter is a calendar period, not one attaché's property — 2024 Q2 is the \
                    same quarter in Cairo and in Amman. The rows stay visible to everyone.""")
                .hasSameSizeAs(ministry);

        long ministryTotal = ministry.stream().mapToLong(q -> q.getQuarterAmount() == null ? 0 : q.getQuarterAmount()).sum();
        long jordanTotal = jordan.stream().mapToLong(q -> q.getQuarterAmount() == null ? 0 : q.getQuarterAmount()).sum();

        assumeTrue(ministryTotal > 0, "no finance rows behind the quarters");

        // What Jordan's own students have actually received. The scoped figure may
        // be less than this — a finance row whose quarter is missing from
        // MOHE_QUARTER has no quarter to be counted against (WD-12) — but it can
        // never be more, and that is the half of it worth asserting.
        Long jordanOwn = jdbc.queryForObject(
                "SELECT ISNULL(SUM(CAST(STUDENT_FIN_RECIEVED_AMOUNT AS bigint)), 0) "
                        + "FROM MOHECORE.STUDENT_FINANCE WHERE STUDY_COUNTRY_IN_ID = ?",
                Long.class, 1004);

        // This read "isZero", which was true only because every student on the
        // database was Egyptian — it proved the scope worked the way an empty
        // cupboard proves a lock works. With students seeded in Amman and Riyadh
        // the figure is real, so the test can state the rule instead: an attaché
        // reads its own students' money, and never the ministry's.
        assertThat(jordanTotal)
                .as("an attaché cannot be shown more than its own students received")
                .isLessThanOrEqualTo(jordanOwn == null ? 0L : jordanOwn);
        assertThat(jordanTotal)
                .as("and cannot be shown the ministry's total, which includes every other country")
                .isLessThan(ministryTotal);
    }

    @Test
    @DisplayName("the quarter budget shown is the caller's attachés, not the ministry's")
    void quarterBudgetIsScoped() {
        List<MoheQuarter> ministry = AsUser.call(AsUser.SUPERADMIN, () -> moheQuarterService.getAll());
        assumeTrue(ministry.stream().anyMatch(q -> q.getBudgetAmount() != null && q.getBudgetAmount() > 0),
                "no allocations rolled up yet");

        assertThat(AsUser.call(AsUser.JO_ADMIN, () -> moheQuarterService.getAll()))
                .as("""
                    MOHE_QUARTER.BUDGET_AMOUNT holds the ministry-wide roll-up and stays that way \
                    — it is a real total. The screen recomputes it per scope instead, or a Jordan \
                    officer reads every attaché's budget off this page.""")
                .allSatisfy(q -> assertThat(q.getBudgetAmount()).isZero());
    }
}
