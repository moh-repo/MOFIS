package com.dilaw.mohe.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every {@code <field>} a template prints is a property of the bean its controller
 * hands it (RPT-00, system test report item 18).
 *
 * <p>{@code scripts/audit_report_data.py} does this statically and reported
 * {@code growth-analysis} as printing two fields no bean supplies. The template was
 * fine: the controller passes {@code service.reportRows(items, lang)}, a list of
 * {@code GrowthAnalysisReportRow}, and the audit had read the type of {@code items}.
 * This test resolves the bean the same way the fixed audit does — the declared
 * return type of the method whose result is passed, else the declared type of the
 * variable passed — and then asks the class itself, through the JavaBeans
 * introspector Jasper uses, rather than parsing its source.
 *
 * <p>No database: it reads {@code src/main/java} and {@code src/main/resources/reports}
 * and loads classes. A template whose bean cannot be resolved from its call site is
 * skipped here (the audit lists those); one that resolves must match.
 */
class TemplateFieldsMatchBeansTest {

    private static final Path JAVA = Path.of("src/main/java");
    private static final Path REPORTS = Path.of("src/main/resources/reports");

    /** Parameters, not data fields — the same prefixes the audit skips. */
    private static final Pattern PARAM_LIKE =
            Pattern.compile("^(TITLE_|SIG_|LBL_|COL_|REPORT_|FOOTER_|IS_RTL|LANG|LOGO_|PRINTED_)");
    private static final Pattern FIELD = Pattern.compile("<field\\s+name=\"([^\"]+)\"");

    private static final List<Pattern> CALL_SITES = List.of(
            Pattern.compile("generatePdf\\(\\s*\"([^\"]+)\"\\s*,\\s*\\w+\\s*,\\s*([^;]*?)\\)\\s*;", Pattern.DOTALL),
            Pattern.compile("render\\(\\s*\"([^\"]+)\"\\s*,\\s*\\w+\\s*,\\s*((?:[^,()]|\\([^()]*\\))+?)\\s*,", Pattern.DOTALL));
    private static final Pattern METHOD_RETURNS = Pattern.compile(
            "\\b(?:java\\.util\\.)?(?:List|Collection)<\\s*([A-Za-z0-9_.]+)\\s*>\\s+([a-z]\\w*)\\s*\\(");
    private static final Pattern METHOD_CALL = Pattern.compile("^\\s*(?:[a-z]\\w*\\s*\\.\\s*)+([a-z]\\w*)\\s*\\(");
    private static final Pattern NESTED = Pattern.compile("\\bstatic\\s+class\\s+(\\w+)");

    private static Map<String, String> javaSources() throws IOException {
        Map<String, String> out = new HashMap<>();
        try (Stream<Path> files = Files.walk(JAVA)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                out.put(JAVA.relativize(p).toString(), Files.readString(p, StandardCharsets.UTF_8));
            }
        }
        return out;
    }

    /** Simple class name → loaded class, for the row packages the audit scans (and their nested classes). */
    private static Map<String, Class<?>> rowClasses(Map<String, String> sources) {
        Map<String, Class<?>> out = new HashMap<>();
        for (Map.Entry<String, String> e : sources.entrySet()) {
            String rel = e.getKey().replace('\\', '/');
            if (!(rel.startsWith("com/dilaw/mohe/model/") || rel.startsWith("com/dilaw/mohe/dto/")
                    || rel.startsWith("com/dilaw/mohe/report/chart/"))) continue;
            String fqcn = rel.substring(0, rel.length() - ".java".length()).replace('/', '.');
            Class<?> top = load(fqcn);
            if (top == null) continue;
            out.putIfAbsent(top.getSimpleName(), top);
            Matcher m = NESTED.matcher(e.getValue());
            while (m.find()) {
                Class<?> nested = load(fqcn + "$" + m.group(1));
                if (nested != null) out.putIfAbsent(m.group(1), nested);
            }
        }
        return out;
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name, false, TemplateFieldsMatchBeansTest.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    /** template → candidate bean simple names, from the Jasper call sites. */
    static Map<String, Set<String>> templateBeans(Map<String, String> sources) {
        Map<String, Set<String>> returns = new HashMap<>();
        for (String src : sources.values()) {
            Matcher m = METHOD_RETURNS.matcher(src);
            while (m.find()) {
                String type = m.group(1);
                returns.computeIfAbsent(m.group(2), k -> new LinkedHashSet<>())
                        .add(type.substring(type.lastIndexOf('.') + 1));
            }
        }
        Map<String, Set<String>> out = new TreeMap<>();
        for (String src : sources.values()) {
            if (!src.contains("generatePdf(") && !src.contains(".render(")) continue;
            for (Pattern site : CALL_SITES) {
                Matcher m = site.matcher(src);
                while (m.find()) {
                    String template = m.group(1);
                    String data = m.group(2).trim();
                    Set<String> beans = out.computeIfAbsent(template, k -> new LinkedHashSet<>());
                    Matcher call = METHOD_CALL.matcher(data);
                    if (call.find() && returns.containsKey(call.group(1))) {
                        beans.addAll(returns.get(call.group(1)));
                        continue;
                    }
                    Matcher types = Pattern.compile("\\b([A-Z][A-Za-z0-9]+)\\b").matcher(data);
                    while (types.find()) beans.add(types.group(1));
                    Matcher vars = Pattern.compile("\\b([a-z]\\w*)\\b").matcher(data);
                    while (vars.find()) {
                        String var = vars.group(1);
                        Matcher decl = Pattern.compile("(?:List|Collection)<\\s*([A-Za-z0-9_.]+)\\s*>\\s+"
                                + Pattern.quote(var) + "\\b").matcher(src);
                        if (decl.find()) {
                            String t = decl.group(1);
                            beans.add(t.substring(t.lastIndexOf('.') + 1));
                        }
                    }
                }
            }
        }
        return out;
    }

    private static Set<String> propertiesOf(Class<?> type) {
        Set<String> props = new TreeSet<>();
        try {
            for (PropertyDescriptor pd : Introspector.getBeanInfo(type).getPropertyDescriptors()) {
                if (pd.getReadMethod() != null) props.add(pd.getName());
            }
        } catch (java.beans.IntrospectionException e) {
            throw new IllegalStateException(e);
        }
        if (type.isRecord()) {
            for (RecordComponent rc : type.getRecordComponents()) props.add(rc.getName());
        }
        return props;
    }

    private static List<String> dataFields(Path template) throws IOException {
        List<String> fields = new ArrayList<>();
        Matcher m = FIELD.matcher(Files.readString(template, StandardCharsets.UTF_8));
        while (m.find()) {
            if (!PARAM_LIKE.matcher(m.group(1)).find()) fields.add(m.group(1));
        }
        return fields;
    }

    @Test
    @DisplayName("growth-analysis resolves to the row its controller actually passes (RPT-00)")
    void growthAnalysisResolvesToItsReportRow() throws IOException {
        assertThat(templateBeans(javaSources()).get("growth-analysis"))
                .as("the controller passes service.reportRows(...), a List<GrowthAnalysisReportRow>")
                .containsExactly("GrowthAnalysisReportRow");
    }

    @Test
    @DisplayName("every template field is a readable property of a bean its controller passes")
    void everyTemplateFieldIsABeanProperty() throws IOException {
        Map<String, String> sources = javaSources();
        Map<String, Class<?>> classes = rowClasses(sources);
        Map<String, Set<String>> beans = templateBeans(sources);

        Map<String, List<String>> missing = new TreeMap<>();
        int checked = 0;
        try (Stream<Path> files = Files.list(REPORTS)) {
            for (Path template : files.filter(p -> p.toString().endsWith(".jrxml")).sorted().toList()) {
                String name = template.getFileName().toString().replace(".jrxml", "");
                List<Class<?>> candidates = beans.getOrDefault(name, Set.of()).stream()
                        .map(classes::get).filter(c -> c != null).<Class<?>>map(c -> c).toList();
                List<String> fields = dataFields(template);
                if (candidates.isEmpty() || fields.isEmpty()) continue;
                checked++;
                Set<String> supplied = new TreeSet<>();
                candidates.forEach(c -> supplied.addAll(propertiesOf(c)));
                for (String f : fields) {
                    if (!supplied.contains(f)) missing.computeIfAbsent(name, k -> new ArrayList<>()).add(f);
                }
            }
        }

        assertThat(checked).as("most templates resolve to a bean; a collapse here means the parsing broke")
                .isGreaterThan(40);
        assertThat(missing).as("template → fields no bound bean supplies").isEmpty();
    }
}
