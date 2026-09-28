package com.dilaw.mohe.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flyway owns the migration history from V097 on (security plan O-29).
 *
 * <p>The context that runs this test has already migrated: the baseline row
 * is at V096, nothing is pending, nothing failed, and every script the jar
 * carries after the baseline is recorded as applied with the checksum it has
 * on disk - which is the property the whole item exists for, because a script
 * edited after it ran would fail validation here before it fails in production.
 */
@Tag("integration")
@SpringBootTest
class FlywayHistoryTest {

    @Autowired private Flyway flyway;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("the history table exists, is baselined at V096 and records no failure")
    void historyIsBaselinedAndClean() {
        Integer failed = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE success = 0", Integer.class);
        assertThat(failed).as("a failed migration stops startup; none may be on record").isZero();

        String baseline = jdbc.queryForObject(
                "SELECT TOP 1 version FROM dbo.flyway_schema_history WHERE type = 'BASELINE' ORDER BY installed_rank",
                String.class);
        assertThat(baseline)
                .as("an existing database is baselined at the last hand-applied script")
                .isEqualTo("96");
    }

    @Test
    @DisplayName("every packaged script above the baseline is applied, and none is pending or failed")
    void nothingPendingNothingFailed() {
        MigrationInfoService info = flyway.info();
        assertThat(info.pending()).as("startup applies whatever the jar carries").isEmpty();
        assertThat(Arrays.stream(info.all()).filter(m -> m.getState().isFailed()).map(MigrationInfo::getScript))
                .as("failed or ignored migrations")
                .isEmpty();
        assertThat(info.current().getVersion().isAtLeast("96")).isTrue();
    }
}
