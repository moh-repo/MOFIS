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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Report templates take their data from Java beans, never from SQL they run
 * themselves (security plan O-12).
 *
 * <p>Jasper's {@code $P!{name}} pastes a parameter's text straight into the
 * query, so a parameter that came from a request would be injectable; the
 * {@code $X{}} clause functions and a {@code <queryString>} exist only for
 * templates that query a connection. Every template here is rendered from a
 * {@code JRBeanCollectionDataSource}, so none of the three has a place: a
 * template that gains one changes the data path and must be reviewed. The same
 * check runs without a JDK in the CI report job ({@code scripts/audit_report_data.py}).
 */
class ReportTemplateHygieneTest {

    private static final Path TEMPLATES = Paths.get("src", "main", "resources", "reports");

    @Test
    @DisplayName("no template substitutes parameters into SQL or carries a query of its own")
    void templatesAreBeanFedOnly() throws IOException {
        List<String> problems = new ArrayList<>();
        int count = 0;
        try (Stream<Path> files = Files.walk(TEMPLATES)) {
            for (Path p : files.filter(f -> f.getFileName().toString().endsWith(".jrxml")).toList()) {
                count++;
                String name = TEMPLATES.relativize(p).toString();
                int lineNo = 0;
                for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                    lineNo++;
                    if (line.contains("$P!{")) problems.add(name + ":" + lineNo + ": raw parameter substitution $P!{}");
                    if (line.contains("$X{")) problems.add(name + ":" + lineNo + ": query clause function $X{}");
                    if (line.contains("<queryString")) problems.add(name + ":" + lineNo + ": template runs its own query");
                }
            }
        }
        assertThat(count).as("templates under %s", TEMPLATES).isGreaterThan(0);
        assertThat(problems)
                .as("templates are filled from beans; a query in a template bypasses the scoped services and, with $P!{}, is injectable")
                .isEmpty();
    }
}
