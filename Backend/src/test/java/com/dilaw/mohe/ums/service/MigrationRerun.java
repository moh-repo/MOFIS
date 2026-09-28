package com.dilaw.mohe.ums.service;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Runs a migration script a second time on the application's pool, for the idempotency tests.
 *
 * <p>The scripts open with {@code SET NOCOUNT ON; SET XACT_ABORT ON}, and a SET option lasts for
 * the connection's session, which the pool keeps. Sent through a plain {@code jdbc.execute}, the
 * connection went back to the pool with both on, and the next test on the thread was handed it:
 * Hibernate read the -1 row count of its next insert as a lost update ({@code MfaServiceTest},
 * "Unexpected row count: -1", in the full run only). The script now runs on one connection, and
 * both options are put back as they were before that connection is returned, whether the script
 * succeeded or not. Flyway is not affected: it runs the migrations on a connection of its own.
 */
final class MigrationRerun {

    /** {@code @@OPTIONS} bits (SQL Server). */
    private static final int XACT_ABORT = 16384;
    private static final int NOCOUNT = 512;

    private MigrationRerun() {
    }

    static void rerun(JdbcTemplate jdbc, Path script) throws IOException {
        String sql = Files.readString(script, StandardCharsets.UTF_8);
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            int options;
            try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery("SELECT @@OPTIONS")) {
                rs.next();
                options = rs.getInt(1);
            }
            try (Statement st = connection.createStatement()) {
                st.execute(sql);
            } finally {
                try (Statement st = connection.createStatement()) {
                    st.execute("SET NOCOUNT " + ((options & NOCOUNT) != 0 ? "ON" : "OFF")
                            + "; SET XACT_ABORT " + ((options & XACT_ABORT) != 0 ? "ON" : "OFF"));
                }
            }
            return null;
        });
    }
}
