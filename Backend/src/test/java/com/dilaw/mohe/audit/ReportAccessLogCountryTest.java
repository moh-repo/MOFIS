package com.dilaw.mohe.audit;

import com.dilaw.mohe.scope.AsUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A report export is logged under the exporting user's country.
 *
 * <p>{@code MOHECORE.REPORT_ACCESS_LOG} is under the tenant policy on
 * {@code STUDY_COUNTRY_IN_ID}, but the entity never mapped the column: every row was
 * NULL and the predicate separated nothing. It also read the user on the async
 * thread, where there is none, so every row said {@code system}.
 *
 * <p>Rolled back: the row written here is gone when the test ends.
 */
@Tag("integration")
@SpringBootTest
@Transactional
class ReportAccessLogCountryTest {

    private static final int JORDAN = 1004;

    @Autowired private ReportAccessLogService service;
    @Autowired private ReportAccessLogRepository repository;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("a single-country user's export writes that country and that user")
    void singleCountryUserIsStamped() {
        ReportAccessLog entry = AsUser.call(AsUser.JO_ADMIN,
                () -> service.prepare("QUARTERLY_FINANCIAL", "PDF", "quarter=3"));

        assertThat(entry.getStudyCountryInId()).isEqualTo(JORDAN);
        assertThat(entry.getUsername())
                .as("read on the request thread, not on the pool thread that has no principal")
                .isEqualTo(String.valueOf(AsUser.JO_ADMIN));

        ReportAccessLog saved = repository.saveAndFlush(entry);
        Integer stored = jdbc.queryForObject(
                "SELECT STUDY_COUNTRY_IN_ID FROM MOHECORE.REPORT_ACCESS_LOG WHERE LOG_ID = ?",
                Integer.class, saved.getLogId());
        assertThat(stored).isEqualTo(JORDAN);
    }

    @Test
    @DisplayName("a ministry user's export is global: NULL, as on every other tenanted table")
    void ministryUserIsGlobal() {
        ReportAccessLog entry = AsUser.call(AsUser.SUPERADMIN,
                () -> service.prepare("CUSTOM_FILTER", "EXCEL", null));

        assertThat(entry.getStudyCountryInId()).isNull();
    }

    @Test
    @DisplayName("a user holding several countries is global too: one row cannot name two attachés")
    void multiCountryUserIsGlobal() {
        ReportAccessLog entry = AsUser.call(AsUser.ADMIN,
                () -> service.prepare("CUSTOM_FILTER", "EXCEL", null));

        assertThat(entry.getStudyCountryInId()).isNull();
    }
}
