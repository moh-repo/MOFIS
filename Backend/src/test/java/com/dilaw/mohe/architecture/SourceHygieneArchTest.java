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
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Rules about the source tree that are cheaper to state as a test than to
 * re-discover in review. Security plan O-31 and S-02 (6 Sep 2026).
 */
class SourceHygieneArchTest {

    private static final Path MAIN = Paths.get("src", "main", "java");

    private static List<Path> javaFiles() throws IOException {
        try (Stream<Path> s = Files.walk(MAIN)) {
            return s.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    @Test
    @DisplayName("O-31: Jackson polymorphic default typing is never enabled")
    void noDefaultTyping() throws IOException {
        assumeTrue(Files.isDirectory(MAIN), "run from the Backend directory");
        Pattern forbidden = Pattern.compile(
                "activateDefaultTyping|enableDefaultTyping|JsonTypeInfo\\.Id\\.CLASS|JsonTypeInfo\\.Id\\.MINIMAL_CLASS");
        List<String> hits = new ArrayList<>();
        for (Path p : javaFiles()) {
            String text = Files.readString(p, StandardCharsets.UTF_8);
            if (forbidden.matcher(text).find()) hits.add(MAIN.relativize(p).toString());
        }
        assertThat(hits)
                .as("polymorphic default typing turns any JSON body into a gadget-chain deserialisation")
                .isEmpty();
    }

    @Test
    @DisplayName("S-02: every tenant-scope elevation says why")
    void everyRunAsSystemIsExplained() throws IOException {
        assumeTrue(Files.isDirectory(MAIN), "run from the Backend directory");
        List<String> unexplained = new ArrayList<>();
        int sites = 0;
        for (Path p : javaFiles()) {
            if (p.endsWith("TenantContext.java")) continue; // the definition
            List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                if (!lines.get(i).contains("runAsSystem(")) continue;
                sites++;
                // A comment within the eight lines above the call, or on the same line.
                boolean explained = lines.get(i).contains("//") || lines.get(i).contains("/*");
                for (int j = Math.max(0, i - 8); j < i && !explained; j++) {
                    String t = lines.get(j).trim();
                    explained = t.startsWith("//") || t.startsWith("*") || t.startsWith("/*");
                }
                if (!explained) unexplained.add(MAIN.relativize(p) + ":" + (i + 1));
            }
        }
        assertThat(sites).as("the elevation is in use").isGreaterThan(0);
        assertThat(unexplained)
                .as("a runAsSystem call reads every country's rows; each one needs a comment saying why that is safe here")
                .isEmpty();
    }
}
