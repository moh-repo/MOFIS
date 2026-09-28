package com.dilaw.mohe.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The database must be rebuildable from {@code docs/scripts}.
 *
 * <p>It was not. Of 50 views, 34 existed only in the database — created by hand
 * in SSMS and never committed — so restoring from these scripts produced a
 * schema missing {@code V_Payment_Summary}, {@code V_Financial_Audit} and 32
 * others, and every report bound to them failed at query time rather than at
 * deploy time. V033 captured them.
 *
 * <p>Nothing stops that happening again except noticing, which is what this
 * test is for. A view created directly in the database fails the build until
 * someone writes the migration that creates it.
 *
 * <p>This checks that a definition <em>exists</em>, not that it matches. Comparing
 * bodies would mean normalising SQL Server's stored text against the script's,
 * and would fail on whitespace while passing on anything genuinely divergent.
 * Presence is the property that actually mattered here.
 */
@Tag("integration")
@SpringBootTest
class ViewsAreInSourceControlTest {

    /** Where the manual migrations live, relative to the Maven module root. */
    private static final Path SCRIPTS = Path.of("docs", "scripts");

    /**
     * Views V066 drops. Their entities now carry the SELECT as a subselect and
     * nothing in {@code src} names them, so a database that still has them is one
     * the migration has not reached yet — not a view somebody created by hand.
     */
    private static final Set<String> DROPPED_BY_V066 = Set.of(
            "V_Fiscal_Year_Report", "V_Custom_Filter_Report", "V_Academic_Year_Report",
            "V_Custom_Period_Report", "V_Predictive_Analytics");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("every view in the database is created by a script in docs/scripts")
    void everyView_hasAMigration() {
        List<String> views = jdbc.queryForList(
                "SELECT name FROM sys.views WHERE SCHEMA_NAME(schema_id) = 'MOHECORE' ORDER BY name",
                String.class);

        assertThat(views)
                .as("no views found — this test is reading the wrong database and proves nothing")
                .isNotEmpty();

        String scripts = allScriptText();
        List<String> missing = new ArrayList<>();
        for (String view : views) {
            if (DROPPED_BY_V066.stream().anyMatch(view::equalsIgnoreCase)) {
                continue;
            }
            if (!definedIn(scripts, view)) {
                missing.add(view);
            }
        }

        assertThat(missing)
                .as("these views exist only in the database, so a restore from docs/scripts "
                        + "would produce a schema the application cannot run against. "
                        + "Add a migration that creates them (V033 is the pattern).")
                .isEmpty();
    }

    /**
     * True if any script creates this view.
     *
     * <p>Both spellings count: SQL Server stores some of these definitions as
     * {@code MOHECORE.V_Thing} and others as {@code [MOHECORE].[V_Thing]}, and a
     * checker that only understood the first reported three views as missing
     * when they were in fact captured — a false alarm is as bad as a missed one
     * for a test meant to be trusted.
     */
    private static boolean definedIn(String scripts, String view) {
        String name = Pattern.quote(view);
        Pattern p = Pattern.compile(
                "VIEW\\s+\\[?MOHECORE\\]?\\s*\\.\\s*\\[?" + name + "\\]?(?![A-Za-z0-9_])",
                Pattern.CASE_INSENSITIVE);
        return p.matcher(scripts).find();
    }

    private static String allScriptText() {
        try (Stream<Path> files = Files.walk(SCRIPTS)) {
            StringBuilder all = new StringBuilder();
            files.filter(f -> f.toString().toLowerCase().endsWith(".sql"))
                    .forEach(f -> {
                        try {
                            all.append(Files.readString(f, StandardCharsets.UTF_8)).append('\n');
                        } catch (IOException e) {
                            // A script written in another encoding still has ASCII
                            // keywords, which is all the regex above needs.
                            try {
                                all.append(new String(Files.readAllBytes(f), StandardCharsets.ISO_8859_1))
                                        .append('\n');
                            } catch (IOException fatal) {
                                throw new UncheckedIOException(fatal);
                            }
                        }
                    });
            return all.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Could not read " + SCRIPTS.toAbsolutePath() + " — is the working directory the module root?", e);
        }
    }
}
