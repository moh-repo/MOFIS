package com.dilaw.mohe.controler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The archives are read-only apart from the student archive's finance-column
 * correction (decision DEC-08 a; live QA H1 of 23 Sep 2026, H2 of 24 Sep 2026).
 *
 * <p>The independent live test of 23 Sep 2026 renamed and then deleted an archived
 * snapshot through {@code PUT} and {@code DELETE /api/v1/studentDataArch/1}: both
 * answered 200 for the super admin although Student History offers neither button,
 * and no audit row was written for the delete. The next day's round did the same
 * to the finance archive ({@code DELETE /api/v1/studentFinanceArch/1} removed
 * student 1's snapshot of 4 June 2026) and found the fee archive writable from
 * its own screen. The endpoints are gone rather than gated more tightly, because
 * a gate is only as good as the permission bundle a future role is given. This
 * test reads the controllers' annotations, so a reintroduced write fails the unit
 * suite without a database.
 */
class StudentArchiveEndpointsTest {

    private static List<Method> handlers(Class<?> controller) {
        return Arrays.stream(controller.getDeclaredMethods())
                .filter(m -> !m.isSynthetic())
                .toList();
    }

    private static List<Method> handlers() {
        return handlers(StudentDataArchController.class);
    }

    @Test
    @DisplayName("the student archive controller declares no POST and no DELETE handler")
    void noCreateNoDelete() {
        assertThat(handlers()).noneMatch(m -> m.isAnnotationPresent(PostMapping.class));
        assertThat(handlers()).noneMatch(m -> m.isAnnotationPresent(DeleteMapping.class));
        assertThat(handlers()).noneMatch(m -> m.isAnnotationPresent(RequestMapping.class));
    }

    @Test
    @DisplayName("the only PUT on the student archive is the finance-column correction")
    void theOnlyPutIsTheFinanceCorrection() {
        List<Method> puts = handlers().stream().filter(m -> m.isAnnotationPresent(PutMapping.class)).toList();
        assertThat(puts).hasSize(1);
        PutMapping mapping = puts.get(0).getAnnotation(PutMapping.class);
        String[] paths = mapping.value().length > 0 ? mapping.value() : mapping.path();
        assertThat(paths).containsExactly("/{id}/finance");
    }

    @Test
    @DisplayName("the finance archive controller declares no write handler at all (live QA H2, 24 Sep 2026)")
    void financeArchiveHasNoWrite() {
        assertNoWrites(StudentFinanceArchController.class);
    }

    @Test
    @DisplayName("the fee archive controller declares no write handler at all (live QA H2, 24 Sep 2026)")
    void feeArchiveHasNoWrite() {
        assertNoWrites(StudyUniversityFeeArchController.class);
    }

    private static void assertNoWrites(Class<?> controller) {
        List<Method> methods = handlers(controller);
        assertThat(methods).as("%s has handlers", controller.getSimpleName()).isNotEmpty();
        assertThat(methods).noneMatch(m -> m.isAnnotationPresent(PostMapping.class));
        assertThat(methods).noneMatch(m -> m.isAnnotationPresent(PutMapping.class));
        assertThat(methods).noneMatch(m -> m.isAnnotationPresent(DeleteMapping.class));
        assertThat(methods).noneMatch(m -> m.isAnnotationPresent(RequestMapping.class));
    }
}
