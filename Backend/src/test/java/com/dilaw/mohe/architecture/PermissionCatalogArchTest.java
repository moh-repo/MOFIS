package com.dilaw.mohe.architecture;

import com.dilaw.mohe.config.PermissionCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Security plan R-11 (6 Sep 2026): every permission code the backend enforces
 * is in {@link PermissionCatalog}, and every catalogue entry is enforced.
 *
 * <p>Source-level, so it needs no context and runs with the unit tests: a new
 * {@code hasAuthority('X')} anywhere under {@code src/main} for an X the
 * catalogue does not list fails the build with the file that introduced it.
 */
class PermissionCatalogArchTest {

    private static final Path MAIN = Paths.get("src", "main", "java");
    private static final Pattern HAS_AUTHORITY = Pattern.compile("hasAuthority\\('([A-Z_0-9.]+)'\\)");

    @Test
    @DisplayName("every hasAuthority(...) in the source names a catalogued code")
    void everyEnforcedCodeIsCatalogued() throws IOException {
        assumeTrue(Files.isDirectory(MAIN), "run from the Backend directory");
        TreeMap<String, TreeSet<String>> unknown = new TreeMap<>();
        TreeSet<String> seen = new TreeSet<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String text = Files.readString(p, StandardCharsets.UTF_8);
                Matcher m = HAS_AUTHORITY.matcher(text);
                while (m.find()) {
                    String code = m.group(1);
                    seen.add(code);
                    if (PermissionCatalog.SYNTHETIC.contains(code) || PermissionCatalog.contains(code)) continue;
                    unknown.computeIfAbsent(code, k -> new TreeSet<>()).add(MAIN.relativize(p).toString());
                }
            }
        }
        assertThat(seen).as("the scan found the expressions").hasSizeGreaterThan(50);
        assertThat(unknown)
                .as("codes enforced in source but absent from PermissionCatalog (add them there, and to the seed script)")
                .isEmpty();
    }

    @Test
    @DisplayName("every catalogue entry is actually enforced somewhere")
    void everyCatalogueEntryIsUsed() throws IOException {
        assumeTrue(Files.isDirectory(MAIN), "run from the Backend directory");
        TreeSet<String> seen = new TreeSet<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                Matcher m = HAS_AUTHORITY.matcher(Files.readString(p, StandardCharsets.UTF_8));
                while (m.find()) seen.add(m.group(1));
            }
        }
        List<String> unused = PermissionCatalog.codes().stream().filter(c -> !seen.contains(c)).sorted().toList();
        assertThat(unused)
                .as("catalogue entries no expression references (remove them, or they are documentation pretending to be enforcement)")
                .isEmpty();
    }

    @Test
    @DisplayName("catalogue codes are well-formed RESOURCE.ACTION and unique")
    void wellFormed() {
        for (PermissionCatalog.Entry e : PermissionCatalog.ENTRIES) {
            assertThat(e.code()).matches("[A-Z][A-Z_0-9]*\\.[A-Z]+");
            assertThat(e.description()).isNotBlank();
        }
        assertThat(PermissionCatalog.codes()).hasSize(PermissionCatalog.ENTRIES.size());
    }
}
