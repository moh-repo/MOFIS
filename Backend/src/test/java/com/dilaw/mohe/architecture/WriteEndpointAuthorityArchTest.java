package com.dilaw.mohe.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No write endpoint is reachable with a read-only permission (FIX-PLAN ESC and
 * live QA H3, 25 Sep 2026).
 *
 * <p>The pattern behind a whole family of findings: a controller puts a read rule
 * on the class ({@code STUDENT_PII}, {@code APPROVALS}, {@code WAFAR}, ...) and
 * never overrides it on POST/PUT/PATCH/DELETE, so an account holding only
 * {@code X.VIEW} could raise information requests, rewrite approval rules, edit
 * family records and upload received-by documents.
 *
 * <p>For every write mapping the effective {@code @PreAuthorize} (method over
 * class) is split into its top-level {@code or} branches. A branch that is a
 * single {@code hasAuthority('X.VIEW' | 'X.EXPORT' | 'X.SEARCH')} — or an
 * {@code and} of nothing else — lets a read-only account write, and fails the
 * test. A verb-aware rule ({@code @httpMethodSecurity.isGetRequest()}) is
 * accepted as is. Handlers that read through a POST, and the few writes a read
 * permission is meant to reach, are listed in {@link #ALLOWED} with the reason.
 */
class WriteEndpointAuthorityArchTest {

    private static final Pattern READ_ONLY = Pattern.compile("hasAuthority\\('[A-Z0-9_]+\\.(VIEW|EXPORT|SEARCH)'\\)");

    /** "Controller#method" -> why a read permission may reach it. */
    private static final Map<String, String> ALLOWED = Map.ofEntries(
            Map.entry("ChartReportController#render",
                    "a read: renders the posted chart data as PDF/Excel, stores nothing"),
            Map.entry("StudentTracerController#getNetworkGraph",
                    "a read: the filter travels in the body"),
            Map.entry("WafarController#requestCorrection",
                    "N-04.e: ministry accounts, which hold WAFAR.VIEW only, propose corrections; "
                            + "WafarSpendingService.requestCorrection admits ministry-level callers only "
                            + "and the result is an approval request for the attaché's administrator"),
            Map.entry("ApprovalController#approveRequest",
                    "live QA H5 / D1-a, D3-a: who signs a level is the chain's configured role, which is not a "
                            + "permission code (Student Officer and Employee approve without APP_PENDING.APPROVE); "
                            + "ApprovalRequestPolicy decides per level, and a level naming nobody needs APP_PENDING.APPROVE"),
            Map.entry("ApprovalController#rejectRequest",
                    "live QA H5: ApprovalRequestPolicy admits the requester, APP_PENDING.REJECT or the waiting "
                            + "level's role; APPROVALS.VIEW alone is refused there")
    );

    @Test
    @DisplayName("no POST/PUT/PATCH/DELETE is reachable with a read-only permission")
    void writesNeedAWritePermission() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Set<String> offenders = new TreeSet<>();
        Set<String> allowedSeen = new TreeSet<>();
        int writes = 0;
        for (BeanDefinition bd : scanner.findCandidateComponents("com.dilaw.mohe")) {
            Class<?> type = Class.forName(bd.getBeanClassName());
            PreAuthorize onClass = AnnotatedElementUtils.findMergedAnnotation(type, PreAuthorize.class);
            for (Method m : type.getDeclaredMethods()) {
                if (!isWrite(m)) continue;
                writes++;
                PreAuthorize onMethod = AnnotatedElementUtils.findMergedAnnotation(m, PreAuthorize.class);
                PreAuthorize effective = onMethod != null ? onMethod : onClass;
                if (effective == null || !readOnlySatisfies(effective.value())) continue;
                String key = type.getSimpleName() + "#" + m.getName();
                if (ALLOWED.containsKey(key)) {
                    allowedSeen.add(key);
                } else {
                    offenders.add(key + "  <-  " + effective.value().trim());
                }
            }
        }
        assertThat(writes).as("the scan found the write mappings").isGreaterThan(100);
        assertThat(offenders)
                .as("write endpoints a read-only permission can reach: add a method-level @PreAuthorize "
                        + "with a write constant (STUDENT_WRITE, FINANCE_WRITE, ...), or list a genuine read in ALLOWED")
                .isEmpty();
        assertThat(allowedSeen).as("ALLOWED entries that no longer match anything (remove them)")
                .containsExactlyInAnyOrderElementsOf(ALLOWED.keySet());
    }

    private static boolean isWrite(Method m) {
        if (AnnotatedElementUtils.hasAnnotation(m, PostMapping.class)
                || AnnotatedElementUtils.hasAnnotation(m, PutMapping.class)
                || AnnotatedElementUtils.hasAnnotation(m, PatchMapping.class)
                || AnnotatedElementUtils.hasAnnotation(m, DeleteMapping.class)) {
            return true;
        }
        RequestMapping rm = m.getAnnotation(RequestMapping.class);
        return rm != null && Arrays.stream(rm.method())
                .anyMatch(v -> v == RequestMethod.POST || v == RequestMethod.PUT
                        || v == RequestMethod.PATCH || v == RequestMethod.DELETE);
    }

    /** Whether some top-level branch of the expression is met by read-only authorities alone. */
    static boolean readOnlySatisfies(String expression) {
        String e = expression.trim();
        if (e.contains("isGetRequest()")) return false;
        for (String branch : splitTopLevel(e, " or ")) {
            if (branchIsReadOnly(branch)) return true;
        }
        return false;
    }

    private static boolean branchIsReadOnly(String branch) {
        String b = unwrap(branch.trim());
        List<String> ors = splitTopLevel(b, " or ");
        if (ors.size() > 1) return ors.stream().anyMatch(WriteEndpointAuthorityArchTest::branchIsReadOnly);
        List<String> ands = splitTopLevel(b, " and ");
        if (ands.size() > 1) return ands.stream().allMatch(WriteEndpointAuthorityArchTest::branchIsReadOnly);
        Matcher m = READ_ONLY.matcher(b);
        return m.matches();
    }

    private static String unwrap(String s) {
        while (s.startsWith("(") && s.endsWith(")") && matchingClose(s) == s.length() - 1) {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s;
    }

    private static int matchingClose(String s) {
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')' && --depth == 0) return i;
        }
        return -1;
    }

    private static List<String> splitTopLevel(String s, String sep) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (depth == 0 && s.startsWith(sep, i)) {
                parts.add(s.substring(start, i).trim());
                start = i + sep.length();
                i = start - 1;
            }
        }
        parts.add(s.substring(start).trim());
        parts.removeIf(String::isEmpty);
        return parts;
    }

    @Test
    @DisplayName("the expression reader sees read-only branches and nothing else")
    void readerSanity() {
        assertThat(readOnlySatisfies("hasAuthority('STUDENTS.VIEW') or hasAuthority('STUDENTS.EDIT')")).isTrue();
        assertThat(readOnlySatisfies("hasAuthority('STUDENTS.EDIT') or hasAuthority('STUDENTS.ADD') ")).isFalse();
        assertThat(readOnlySatisfies("(@httpMethodSecurity.isGetRequest() and hasAuthority('SETTINGS.VIEW')) or hasAuthority('SETTINGS.EDIT')")).isFalse();
        assertThat(readOnlySatisfies("hasAuthority('STUDENT_INFO_REQUESTS.EDIT')  and (hasAuthority('STUDENTS.EDIT') or hasAuthority('STUDENTS.VIEW'))")).isFalse();
        assertThat(readOnlySatisfies("(hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_MINISTRY_ADMIN')) and hasAuthority('UMS_ADMIN.VIEW')")).isFalse();
        assertThat(readOnlySatisfies("hasAuthority('REPORTS.VIEW') and hasAuthority('FINANCE.EXPORT')")).isTrue();
    }
}
