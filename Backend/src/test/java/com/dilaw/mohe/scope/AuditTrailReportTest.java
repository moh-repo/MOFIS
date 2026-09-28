package com.dilaw.mohe.scope;

import com.dilaw.mohe.dto.AuditTrailRowDto;
import com.dilaw.mohe.service.AuditTrailReportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The audit trail report, whose whole design is refusing to be too large.
 *
 * <p>The trail holds over eleven thousand rows and the last thirty days alone
 * are more than nine thousand. Truncating would produce an incomplete audit
 * document that looks complete — the exact failure an audit trail exists to
 * prevent — so the service refuses instead, and these tests pin that refusal
 * down as behaviour rather than leaving it to a comment.
 */
@Tag("integration")
@SpringBootTest
class AuditTrailReportTest {

    @Autowired private AuditTrailReportService reportService;
    @Autowired private JdbcTemplate jdbc;

    private static final LocalDate FROM = LocalDate.of(2026, 8, 1);
    private static final LocalDate TO = LocalDate.of(2026, 8, 22);

    @Test
    @DisplayName("a missing date range is refused, not silently widened")
    void rangeIsRequired() {
        assertThatThrownBy(() -> AsUser.call(AsUser.SUPERADMIN,
                () -> reportService.rows(null, null, null, null, null, null, "en")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("date range is required");

        assertThatThrownBy(() -> AsUser.call(AsUser.SUPERADMIN,
                () -> reportService.rows(TO, FROM, null, null, null, null, "en")))
                .as("an inverted range is a mistake worth naming, not an empty result")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a range over the cap is refused with the count, not truncated")
    void overCapIsRefusedWithTheCount() {
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM AUDITLOG.AuditTrail", Integer.class);
        assumeTrue(total != null && total > 2000, "not enough audit rows to exceed the cap");

        assertThatThrownBy(() -> AsUser.call(AsUser.SUPERADMIN,
                () -> reportService.rows(LocalDate.of(2020, 1, 1), LocalDate.now(),
                        null, null, null, null, "en")))
                .as("""
                    Truncating would produce an incomplete audit that looks complete. \
                    The message carries the actual count so the caller knows how far to narrow.""")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("more than the");
    }

    @Test
    @DisplayName("a range within the cap returns every matching row, not a page of them")
    void withinCapReturnsEverything() {
        long expected = AsUser.call(AsUser.SUPERADMIN,
                () -> reportService.count(FROM, TO, "Users", null, null, null));
        assumeTrue(expected > 0 && expected <= 2000, "no Users changes in the window");

        List<AuditTrailRowDto> rows = AsUser.call(AsUser.SUPERADMIN,
                () -> reportService.rows(FROM, TO, "Users", null, null, null, "en"));

        assertThat(rows)
                .as("the report is the whole answer for its range, or it is not an audit")
                .hasSize((int) expected);
        assertThat(rows).allSatisfy(r -> assertThat(r.getTableName()).isEqualTo("Users"));
    }

    @Test
    @DisplayName("a country officer sees less of the trail than the ministry")
    void officerSeesLessThanMinistry() {
        long ministry = AsUser.call(AsUser.SUPERADMIN,
                () -> reportService.count(FROM, TO, null, null, null, null));
        long jordan = AsUser.call(AsUser.JO_ADMIN,
                () -> reportService.count(FROM, TO, null, null, null, null));

        assumeTrue(ministry > 0, "no audit rows in the window");
        assertThat(jordan)
                .as("""
                    A Users change carries no country and is ministry-only; the ministry's own \
                    administrative history is not one attaché's business.""")
                .isLessThan(ministry);
    }

    @Test
    @DisplayName("the JSON column list is flattened, so the page never prints raw JSON")
    void changedColumnsAreReadable() {
        List<AuditTrailRowDto> rows = AsUser.call(AsUser.SUPERADMIN,
                () -> reportService.rows(FROM, TO, "Users", null, null, null, "en"));
        assumeTrue(!rows.isEmpty(), "no Users changes in the window");

        assertThat(rows)
                .filteredOn(r -> r.getChangedColumns() != null)
                .allSatisfy(r -> assertThat(r.getChangedColumns())
                        .doesNotContain("[")
                        .doesNotContain("\""));
    }

    @Test
    @DisplayName("the count and the rows agree, so the screen's warning is not a guess")
    void countMatchesRows() {
        long counted = AsUser.call(AsUser.SUPERADMIN,
                () -> reportService.count(FROM, TO, "Users", null, null, null));
        assumeTrue(counted > 0 && counted <= 2000, "no Users changes in the window");

        assertThat(AsUser.call(AsUser.SUPERADMIN,
                () -> reportService.rows(FROM, TO, "Users", null, null, null, "en")))
                .hasSize((int) counted);
    }
}
