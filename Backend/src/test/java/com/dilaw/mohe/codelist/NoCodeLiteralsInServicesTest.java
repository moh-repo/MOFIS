package com.dilaw.mohe.codelist;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The literal watchdog. Status and severity codes belong to the {@code codes/}
 * package and the database; a quoted {@code "APPROVED"} anywhere else in the
 * main sources is a regression of the 30 Aug 2026 pass. Comments are stripped
 * first, so a comment that names a code is not a hit. The allow-list names the
 * files the owner decided to leave as they are (O4) and the ones where the word
 * is not a code at all.
 */
class NoCodeLiteralsInServicesTest {

    private static final Pattern CODE_LITERAL = Pattern.compile(
            "\"(OPEN|ACKNOWLEDGED|RESOLVED|DISMISSED|CRITICAL|HIGH|MEDIUM|LOW|APPROVED|PENDING|REJECTED|CANCELLED"
                    + "|IN_PROGRESS|EXECUTED|TRANSFERRED|MATCHED|DISCREPANCY|DRAFT|PAID|SENT|PARTIAL|FAILED|LOCKED"
                    + "|INACTIVE|DISBURSED|CLOSED|REVISED|RUNNING|SUCCESS|WARNING|ERROR|INFO|OVERDUE|RESPONDED"
                    + "|PENDING_REVIEW|GENERAL)\"");

    /** Files that may still carry such a word: not a code there, or kept by the owner's decision. */
    private static final Set<String> ALLOWED = Set.of(
            "ScholarshipEtlService.java",          // O4: the Excel ETL stays as it is
            "ScholarshipDataImportService.java",   // O4
            "CountryScopeService.java",            // "MINISTRY" is a scope kind, not a code list
            "QueryExecutionService.java",          // SQL grammar allow-lists
            "BackupService.java",                  // SQL Server backup vocabulary
            "StudentPortalMeController.java",      // "GENERAL" document type default (owner item, recorded)
            "ReportParamBuilder.java",             // handled by V077 (report labels)
            "AlertController.java",                // SSE event names
            "StatisticsService.java"               // legacy table-counts body: "HEALTHY" / "WARNING" is a health flag, not a code list
    );

    @Test
    @DisplayName("no status, severity or type code is written as a string literal outside the codes package")
    void noCodeLiteralsOutsideTheCodesPackage() throws IOException {
        Path root = Path.of("src", "main", "java", "com", "dilaw", "mohe");
        assertThat(root).isDirectory();
        List<String> hits = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String rel = root.relativize(p).toString().replace('\\', '/');
                if (rel.startsWith("codes/") || ALLOWED.contains(p.getFileName().toString())) continue;
                String src = stripComments(Files.readString(p, StandardCharsets.UTF_8));
                Matcher m = CODE_LITERAL.matcher(src);
                while (m.find()) {
                    int line = 1 + (int) src.substring(0, m.start()).chars().filter(c -> c == '\n').count();
                    hits.add(rel + ":" + line + " " + m.group());
                }
            }
        }
        assertThat(hits)
                .as("code literals outside codes/ — use the constants of com.dilaw.mohe.codes, which the startup guard verifies")
                .isEmpty();
    }

    private static String stripComments(String src) {
        // Block comments, then line comments; string literals are not parsed, which is
        // conservative — a "//" inside a literal drops the rest of that line only.
        String noBlock = src.replaceAll("(?s)/\\*.*?\\*/", "")
                // a column named like a code (@Column(name = "TRANSFERRED")) is a column, not a code
                .replaceAll("name\\s*=\\s*\"[A-Z_]+\"", "name = _");
        StringBuilder out = new StringBuilder();
        for (String line : noBlock.split("\n", -1)) {
            int i = line.indexOf("//");
            out.append(i >= 0 ? line.substring(0, i) : line).append('\n');
        }
        return out.toString();
    }
}
