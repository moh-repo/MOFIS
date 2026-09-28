package com.dilaw.mohe.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scripts that Flyway will run must be scripts Flyway can run (O-29).
 *
 * <p>Everything through V096 was applied by hand with {@code sqlcmd} and is
 * below the baseline, so its {@code :setvar} lines are history. From V097 on a
 * script is executed by the JDBC driver: no sqlcmd directives, no
 * {@code $(variable)} substitution, a name Flyway parses, and one version per
 * number.
 */
class MigrationScriptHygieneTest {

    private static final Path SCRIPTS = Paths.get("docs", "scripts");
    private static final int BASELINE = 96;
    private static final Pattern NAME = Pattern.compile("V(\\d+)__[A-Za-z0-9_]+\\.sql");

    @Test
    @DisplayName("scripts above the baseline carry no sqlcmd-only syntax and have unique, well-formed names")
    void scriptsAboveBaselineAreFlywayCompatible() throws IOException {
        List<String> problems = new ArrayList<>();
        List<Integer> versions = new ArrayList<>();
        try (Stream<Path> files = Files.list(SCRIPTS)) {
            for (Path p : files.filter(f -> f.getFileName().toString().endsWith(".sql")).toList()) {
                String name = p.getFileName().toString();
                Matcher m = NAME.matcher(name);
                if (!m.matches()) {
                    problems.add(name + ": not a Flyway name (V<n>__<description>.sql)");
                    continue;
                }
                int version = Integer.parseInt(m.group(1));
                if (versions.contains(version)) problems.add(name + ": duplicate version " + version);
                versions.add(version);
                if (version <= BASELINE) continue;
                for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                    String t = line.trim();
                    if (t.startsWith(":") || t.contains("$(")) {
                        problems.add(name + ": sqlcmd-only syntax - " + t);
                    }
                }
            }
        }
        assertThat(problems)
                .as("Flyway executes every script above V%d through JDBC; these would fail or, worse, run with the placeholder text", BASELINE)
                .isEmpty();
        assertThat(versions).as("there are scripts").isNotEmpty();
    }
}
