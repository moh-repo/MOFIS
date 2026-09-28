package com.dilaw.mohe.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The bilingual report views and the entities that map them must stay in step.
 *
 * <p>{@code spring.jpa.hibernate.ddl-auto=none}, so nothing checks the mapping at
 * startup: an entity that maps {@code STUDENT_COUNTRY_NAME_EN} against a view
 * that does not expose it fails when someone runs the report, which is the worst
 * possible moment to find out. This test compares the two sides statically.
 *
 * <p>It reads the migrations rather than the database on purpose — it has to
 * pass on a machine with no SQL Server, and the migration is the statement of
 * intent that the database is supposed to match.
 */
class BilingualViewMappingTest {

    private static final Path SCRIPTS = Paths.get("docs", "scripts");
    private static final Path MODEL = Paths.get("src", "main", "java", "com", "dilaw", "mohe", "model");

    /** The entities V104, V105, V107, V109 and V124 gave English name columns. */
    private static final Set<String> BILINGUAL_ENTITIES = Set.of(
            // V104 — student views
            "StudentListDetailed", "StudentStatusReport", "StudentDemographics", "AcademicPerformance",
            // V105 — university, statistics and payment views
            "ProgramCatalog", "FeeStructure", "StudentDistributionUniversity", "UniversityAgreement",
            "UniversityPartnership", "UniversityPerformance", "EnrollmentStatistics", "MajorDistribution",
            "PaymentSummary",
            // V107 — the financial data forms and the last two statistics views
            "StudentFinancialDataForm", "StudentFinancialDataFormV2", "ScholarshipTracking",
            "ComparativeStatistics",
            // V109 — the two reports that printed constants where data belonged
            "DataVisualization", "DistributionStatistics",
            // V124 — the export manager, the last Arabic-only report view (QA B6)
            "ExportManager");
    // BankTransaction and FinancialAudit are bilingual too, but carry their SQL
    // in an @Subselect rather than mapping a view. There are no two sides to
    // drift apart, so there is nothing here to check: adding them would only
    // assert that a migration creates a column their own query defines.

    /**
     * A view column whose name ends in {@code _EN}, however it is expressed.
     *
     * <p>Keyed on the alias rather than on the fallback expression: the columns
     * are written as {@code COALESCE(NULLIF(…))} in V104–V107 but at least one
     * older one uses {@code ISNULL(NULLIF(…))}, and a detector that recognised
     * only the newer shape reported that column as unbacked when it was not.
     */
    private static final Pattern CREATED =
            Pattern.compile("\\bAS\\s+([A-Za-z_]+_[Ee][Nn])\\s*,?\\s*$");

    /** `@Column(name = "X_EN")` immediately above `private String xEn;` */
    private static final Pattern MAPPED = Pattern.compile(
            "@Column\\(name = \"([A-Za-z_]+)\"\\)\\s*\\n\\s*private String (\\w+En);");

    private static Set<String> columnsCreatedByMigrations() throws IOException {
        Set<String> created = new TreeSet<>();
        try (Stream<Path> files = Files.list(SCRIPTS)) {
            for (Path p : files.filter(f -> f.getFileName().toString().endsWith(".sql")).toList()) {
                for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                    Matcher m = CREATED.matcher(line.trim());
                    if (m.find()) created.add(m.group(1).toUpperCase());
                }
            }
        }
        return created;
    }

    /**
     * Every mapped English column, as {@code COLUMN -> Entity.property}.
     *
     * <p>Keyed on the pair, not on the column: four entities map
     * {@code STUDENT_COUNTRY_NAME_EN}, and keying on the column alone would keep
     * only the last of them and quietly excuse the other three.
     */
    private static Map<String, String> columnsMappedByEntities() throws IOException {
        Map<String, String> mapped = new LinkedHashMap<>();
        for (String entity : new TreeSet<>(BILINGUAL_ENTITIES)) {
            Path p = MODEL.resolve(entity + ".java");
            assertThat(p).as("%s is listed as bilingual but does not exist", entity).exists();
            Matcher m = MAPPED.matcher(Files.readString(p, StandardCharsets.UTF_8));
            while (m.find()) {
                mapped.put(entity + "." + m.group(2), m.group(1).toUpperCase());
            }
        }
        return mapped;
    }

    @Test
    @DisplayName("every English column an entity maps is created by a migration")
    void everyMappedEnglishColumnExists() throws IOException {
        Set<String> created = columnsCreatedByMigrations();
        Map<String, String> mapped = columnsMappedByEntities();

        Set<String> unbacked = new TreeSet<>();
        mapped.forEach((where, column) -> {
            if (!created.contains(column)) {
                unbacked.add(column + " (" + where + ")");
            }
        });

        assertThat(unbacked)
                .as("these columns are mapped but no migration creates them, so the report "
                    + "fails when it is run rather than when the application starts")
                .isEmpty();
    }

    @Test
    @DisplayName("the bilingual entities actually carry English siblings")
    void bilingualEntitiesAreMapped() throws IOException {
        Map<String, String> mapped = columnsMappedByEntities();

        // Each listed entity should contribute at least one; an empty one means a
        // rewrite silently dropped the English columns.
        Set<String> contributing = new TreeSet<>();
        mapped.keySet().forEach(k -> contributing.add(k.substring(0, k.indexOf('.'))));

        assertThat(contributing)
                .as("every entity listed as bilingual should map at least one English column")
                .containsExactlyInAnyOrderElementsOf(new TreeSet<>(BILINGUAL_ENTITIES));
    }

    /**
     * A native query that returns a bilingual entity must select its English
     * columns.
     *
     * <p>Hibernate maps a native result set by column name, so an entity that
     * maps {@code dimension_name_en} against a query that does not select it
     * fails the whole call with {@code Unable to find column position by name}.
     * Not the report — the call: the screen and the export both break.
     *
     * <p>This is not hypothetical. V105 gave {@code PaymentSummary} six English
     * columns and V107 gave {@code ComparativeStatistics} one, and in both cases
     * the entity reads a view while the page reads a native query in the
     * repository that was never updated. Both shipped broken, and the default
     * test run could not see it because the tests that exercise them are
     * {@code @Tag("integration")} and excluded.
     */
    @Test
    @DisplayName("a native query returning a bilingual entity selects its English columns")
    void nativeQueriesSelectTheEnglishColumns() throws IOException {
        Path repos = Paths.get("src", "main", "java", "com", "dilaw", "mohe", "repository");
        Map<String, String> mapped = columnsMappedByEntities();

        Set<String> unselected = new TreeSet<>();
        for (String entity : new TreeSet<>(BILINGUAL_ENTITIES)) {
            Path repo = repos.resolve(entity + "Repository.java");
            if (!Files.exists(repo)) continue;
            String src = Files.readString(repo, StandardCharsets.UTF_8);
            if (!src.contains("nativeQuery = true")) continue;

            // Only queries that hydrate the entity itself are at risk; a
            // projection or a scalar list maps nothing.
            if (!src.contains("<" + entity + ">")) continue;

            mapped.forEach((where, column) -> {
                if (!where.startsWith(entity + ".")) return;
                if (!src.toLowerCase().contains(column.toLowerCase())) {
                    unselected.add(column + " (" + where + " — " + entity + "Repository)");
                }
            });
        }

        assertThat(unselected)
                .as("the entity maps these columns but its native query never selects them, so "
                    + "every call that returns the entity fails on the missing column")
                .isEmpty();
    }

    @Test
    @DisplayName("an English sibling is a String property named <base>En, which is what BilingualRows pairs on")
    void siblingsFollowTheNamingConventionBilingualRowsRelieson() throws IOException {
        Map<String, String> mapped = columnsMappedByEntities();

        for (String where : mapped.keySet()) {
            String entity = where.substring(0, where.indexOf('.'));
            String property = where.substring(where.indexOf('.') + 1);
            String base = property.substring(0, property.length() - "En".length());
            String src = Files.readString(MODEL.resolve(entity + ".java"), StandardCharsets.UTF_8);
            assertThat(src)
                    .as("%s has no sibling `%s` for BilingualRows to overwrite", where, base)
                    .contains("private String " + base + ";");
        }
    }
}
