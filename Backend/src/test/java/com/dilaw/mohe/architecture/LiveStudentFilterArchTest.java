package com.dilaw.mohe.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No hand-written SQL reads students or their finance rows without excluding
 * deleted students (live QA H7, decision D5-a: "one shared filter so no report
 * misses it").
 *
 * <p>For every {@code FROM}/{@code JOIN} (upper case, as all SQL here is written) of {@code MOHECORE.STUDENT_DATA} or
 * {@code MOHECORE.STUDENT_FINANCE} in main code (comments stripped), the Java
 * statement that holds it (from the previous {@code ;}, <code>{</code> or
 * <code>}</code> to the next {@code ;}) must name the filter: {@code IS_DELETED},
 * or {@code LiveStudentSql} / its constants. JPA reads of {@code StudentDatum} are
 * filtered by the entity's {@code @Where} and are not SQL text, so they do not
 * appear here. Writes and the few reads that must see deleted students are listed
 * in {@link #ALLOWED} with the reason.
 */
class LiveStudentFilterArchTest {

    private static final Path MAIN = Paths.get("src", "main", "java");

    private static final Pattern READ = Pattern.compile(
            "\\b(FROM|JOIN)\\s+(\\[?MOHECORE\\]?\\.)?\\[?STUDENT_(DATA|FINANCE)\\]?(?![A-Za-z0-9_])");

    private static final Pattern FILTER = Pattern.compile(
            "IS_DELETED|isDeleted|LiveStudentSql|SD_NOT_DELETED|SF_OF_LIVE_STUDENT|OF_LIVE_STUDENT|notDeleted\\(|ofLiveStudent\\(");

    /**
     * JPQL reads of the finance entity (FIX-PLAN P5-11: {@code sumReceivedAmount} summed
     * deleted students and the SQL scan above cannot see JPQL). The archive,
     * {@code StudentFinanceArch}, is read whole on purpose and is not matched.
     */
    private static final Pattern JPQL_FINANCE_READ = Pattern.compile("\\bFROM\\s+StudentFinance\\s+[a-z]");

    /** File name -> a text in the statement that marks the allowed occurrence, and why. */
    private static final Map<String, List<Allowed>> ALLOWED = Map.of(
            "DataRetentionJob.java", List.of(new Allowed("JOIN MOHECORE.STUDENT_STUDY_STATUS s ON s.STUDENT_STUDY_STATUS_ID = d.STUDENT_STUDY_STATUS_ID",
                    "the retention job anonymises by age, deleted students included on purpose (see studentCandidates)")),
            "PaymentBatchService.java", List.of(new Allowed("SELECT STUDENT_ID, STUDENT_NAME FROM MOHECORE.STUDENT_DATA WHERE STUDENT_ID IN",
                    "names the students on an existing batch's lines; a line paid to a student deleted "
                            + "since then keeps its name")));

    private record Allowed(String marker, String reason) {}

    @Test
    @DisplayName("every SQL read of STUDENT_DATA / STUDENT_FINANCE excludes deleted students")
    void everyReadExcludesDeletedStudents() throws IOException {
        Set<String> offenders = new TreeSet<>();
        int reads = 0;
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String code = stripComments(Files.readString(file, StandardCharsets.UTF_8));
                Matcher m = READ.matcher(code);
                while (m.find()) {
                    reads++;
                    String statement = statementAround(code, m.start());
                    if (FILTER.matcher(statement).find() || allowed(file, statement)) continue;
                    offenders.add(file.getFileName() + ": " + firstLine(code, m.start()));
                }
            }
        }
        assertThat(reads).as("the scan found the SQL").isGreaterThan(50);
        assertThat(offenders)
                .as("SQL reading STUDENT_DATA / STUDENT_FINANCE without excluding deleted students: add "
                        + "LiveStudentSql (or ISNULL(x.IS_DELETED, 0) = 0) to the statement, or list a genuine "
                        + "exception in ALLOWED")
                .isEmpty();
    }

    private static boolean allowed(Path file, String statement) {
        return ALLOWED.getOrDefault(file.getFileName().toString(), List.of()).stream()
                .anyMatch(a -> statement.contains(a.marker()));
    }

    /** The Java statement around {@code at}: previous ; { or } up to the next ;. */
    static String statementAround(String code, int at) {
        int start = Math.max(Math.max(code.lastIndexOf(';', at), code.lastIndexOf('{', at)), code.lastIndexOf('}', at));
        int end = code.indexOf(';', at);
        return code.substring(start + 1, end < 0 ? code.length() : end);
    }

    private static String firstLine(String code, int at) {
        int s = code.lastIndexOf('\n', at) + 1;
        int e = code.indexOf('\n', at);
        return code.substring(s, e < 0 ? code.length() : e).trim();
    }

    /** Drops block and line comments; string literals are kept (SQL has no // in this code base). */
    static String stripComments(String code) {
        String noBlocks = code.replaceAll("(?s)/\\*.*?\\*/", " ");
        return noBlocks.replaceAll("(?m)^\\s*//.*$", " ").replaceAll("(?m)\\s//[^\"\\n]*$", " ");
    }

    @Test
    @DisplayName("every JPQL read of StudentFinance excludes deleted students")
    void everyJpqlFinanceReadExcludesDeletedStudents() throws IOException {
        Set<String> offenders = new TreeSet<>();
        int reads = 0;
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String code = stripComments(Files.readString(file, StandardCharsets.UTF_8));
                Matcher m = JPQL_FINANCE_READ.matcher(code);
                while (m.find()) {
                    reads++;
                    String statement = statementAround(code, m.start());
                    // A NOT EXISTS probe for a missing finance row reads no finance amounts.
                    if (FILTER.matcher(statement).find() || statement.contains("NOT EXISTS (SELECT 1 FROM StudentFinance")) continue;
                    offenders.add(file.getFileName() + ": " + firstLine(code, m.start()));
                }
            }
        }
        assertThat(reads).as("the scan found the JPQL").isGreaterThan(2);
        assertThat(offenders).as("JPQL reading StudentFinance without excluding deleted students").isEmpty();
    }

    @Test
    @DisplayName("the statement reader keeps a query's filter with its FROM")
    void readerSanity() {
        String code = "a(); String sql = \"SELECT 1 FROM MOHECORE.STUDENT_FINANCE sf \" + \" WHERE \" + "
                + "LiveStudentSql.ofLiveStudent(\"sf\"); b();";
        int at = code.indexOf("FROM");
        assertThat(FILTER.matcher(statementAround(code, at)).find()).isTrue();
        String bare = "a(); String sql = \"SELECT COUNT(*) FROM MOHECORE.STUDENT_DATA\"; String x = \"IS_DELETED\";";
        assertThat(FILTER.matcher(statementAround(bare, bare.indexOf("FROM"))).find()).isFalse();
        assertThat(READ.matcher("FROM MOHECORE.STUDENT_DATA_ARCH a").find()).isFalse();
        assertThat(READ.matcher("JOIN MOHECORE.STUDENT_FINANCE_PAYMENT p").find()).isFalse();
        assertThat(READ.matcher("JOIN [MOHECORE].[STUDENT_DATA] t0").find()).isTrue();
    }
}
