package com.dilaw.mohe.security;

import com.dilaw.mohe.approval.controler.ApprovalConfigController;
import com.dilaw.mohe.controler.LookupController;
import com.dilaw.mohe.controler.StudentDataController;
import com.dilaw.mohe.controler.StudentFinanceArchController;
import com.dilaw.mohe.controler.StudentTracerController;
import com.dilaw.mohe.controler.StudentTravelTicketController;
import com.dilaw.mohe.controler.StudyAmountLookupController;
import com.dilaw.mohe.controler.StudyFinanceBankBranchController;
import com.dilaw.mohe.controler.StudyFinanceBankController;
import com.dilaw.mohe.controler.StudyYearsLookupController;
import com.dilaw.mohe.controler.WafarReconciliationController;
import com.dilaw.mohe.dto.TracerFilterDto;
import com.dilaw.mohe.model.StudyAmountLookup;
import com.dilaw.mohe.model.StudyYearsLookup;
import com.dilaw.mohe.notification.controller.NotificationController;
import com.dilaw.mohe.ums.controler.UmsUserController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.expression.AccessException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live QA M6 (25 Sep 2026): screens a role may open made one call the role could not
 * make, and the user got an "Access denied" toast. Each read below is now open to the
 * role that opens the screen, and where a read was widened it returns names or ids only.
 *
 * <p>The effective {@code @PreAuthorize} (the method's, else the class's) is evaluated as
 * Spring evaluates it, against the codes a role holds.
 */
class ScreenReadGatesTest {

    @Test
    @DisplayName("Pending Approvals: travel funding sources are reference data, readable by an approver")
    void fundingSources() throws Exception {
        Method m = StudentTravelTicketController.class.getMethod("getFundingSources", String.class);
        assertThat(allows(m, true, "APPROVALS.VIEW")).isTrue();
    }

    @Test
    @DisplayName("Approval Rules: role names under the page's own read code, ids and names only")
    void roleOptions() throws Exception {
        Method m = ApprovalConfigController.class.getMethod("getRoleOptions");
        assertThat(allows(m, true, "APPROVALS.VIEW")).isTrue();
        assertThat(allows(m, true, "STUDENTS.VIEW")).isFalse();
        assertThat(components(ApprovalConfigController.RoleOption.class)).containsExactly("id", "roleName", "roleNameAr");
    }

    @Test
    @DisplayName("Approval Rules: an approval-rule editor may list approver candidates; a viewer may not")
    void approverCandidates() throws Exception {
        Method m = UmsUserController.class.getMethod("approverCandidates", String.class, Integer.class);
        assertThat(allows(m, true, "APP_CONFIG.EDIT")).isTrue();
        assertThat(allows(m, true, "UMS_ADMIN.VIEW")).isTrue();
        assertThat(allows(m, true, "APPROVALS.VIEW")).isFalse();
    }

    @Test
    @DisplayName("All Students / wizard: pending-change marker readable with the student code, not the approvals one")
    void pendingStudentIds() throws Exception {
        Method m = StudentDataController.class.getMethod("getPendingStudentIds");
        assertThat(allows(m, true, "STUDENTS.VIEW")).isTrue();
        assertThat(allows(m, true, "APPROVALS.VIEW")).isFalse();
    }

    @Test
    @DisplayName("Reconciliation: a Wafar viewer reads it; running a pass still needs a Wafar write code")
    void reconciliation() throws Exception {
        Method list = Arrays.stream(WafarReconciliationController.class.getMethods())
                .filter(x -> x.getName().equals("getAll")).findFirst().orElseThrow();
        Method run = WafarReconciliationController.class.getMethod("runReconciliation");
        assertThat(allows(list, true, "WAFAR.VIEW")).isTrue();
        assertThat(allows(list, true, "FINANCE.VIEW")).isTrue();
        assertThat(allows(run, false, "WAFAR.VIEW")).isFalse();
    }

    @Test
    @DisplayName("Finance History: the Archiving Officer's FINANCE_ARCH.VIEW reads the page's rows")
    void financeHistory() throws Exception {
        Method m = StudentFinanceArchController.class.getMethod("getByStudent", Integer.class);
        assertThat(allows(m, true, "FINANCE_ARCH.VIEW")).isTrue();
        assertThat(allows(m, true, "FINANCE.EXPORT")).isTrue();
        assertThat(allows(m, true, "STUDENTS.VIEW")).isFalse();
    }

    @Test
    @DisplayName("Send Notification: group names under the send permission, ids and names only")
    void recipientGroups() throws Exception {
        Method m = NotificationController.class.getMethod("recipientGroups");
        assertThat(allows(m, true, "NOTIFICATIONS.SEND")).isTrue();
        assertThat(allows(m, true, "NOTIFICATIONS.VIEW")).isFalse();
        assertThat(components(NotificationController.GroupOption.class)).containsExactly("id", "name", "nameAr");
    }

    @Test
    @DisplayName("R5-08 Study Years, Study Amounts, Banks, Branches: a settings viewer reads the list; the old readers still do; writes unchanged")
    void settingsLists() throws Exception {
        Method years = StudyYearsLookupController.class.getMethod("getAll");
        Method amounts = StudyAmountLookupController.class.getMethod("getAll");
        Method banks = StudyFinanceBankController.class.getMethod("getAll");
        Method branches = StudyFinanceBankBranchController.class.getMethod("getAll");
        for (Method list : new Method[] {years, amounts, banks, branches}) {
            assertThat(allows(list, true, "SETTINGS.VIEW")).as("%s, settings viewer", list).isTrue();
            assertThat(allows(list, true, "SETTINGS.EDIT")).as("%s, settings editor", list).isTrue();
            assertThat(allows(list, true, "REPORTS.VIEW")).as("%s, report viewer", list).isFalse();
        }
        assertThat(allows(years, true, "STUDENTS.VIEW")).as("Add Student resolves the years").isTrue();
        assertThat(allows(amounts, true, "STUDENTS.VIEW")).as("Add Student resolves the amount").isTrue();
        assertThat(allows(banks, true, "FINANCE.VIEW")).isTrue();
        assertThat(allows(branches, true, "FINANCE.VIEW")).isTrue();

        assertThat(allows(StudyYearsLookupController.class.getMethod("create", StudyYearsLookup.class), false, "SETTINGS.VIEW")).isFalse();
        assertThat(allows(StudyAmountLookupController.class.getMethod("create", StudyAmountLookup.class), false, "SETTINGS.VIEW")).isFalse();
        assertThat(allows(StudyFinanceBankController.class.getMethod("delete", Integer.class), false, "SETTINGS.VIEW")).isFalse();
        assertThat(allows(StudyFinanceBankBranchController.class.getMethod("delete", Integer.class), false, "SETTINGS.VIEW")).isFalse();
    }

    @Test
    @DisplayName("R5-08 widening, every endpoint: a settings viewer passes each GET (by id and the /lookup queries too), no write")
    void settingsViewerEveryEndpoint() {
        Class<?>[] controllers = {StudyYearsLookupController.class, StudyAmountLookupController.class,
                StudyFinanceBankController.class, StudyFinanceBankBranchController.class};
        int reads = 0, writes = 0;
        for (Class<?> c : controllers) {
            for (Method m : c.getDeclaredMethods()) {
                if (AnnotatedElementUtils.hasAnnotation(m, GetMapping.class)) {
                    reads++;
                    assertThat(allows(m, true, "SETTINGS.VIEW")).as("%s, settings viewer", m).isTrue();
                    assertThat(allows(m, true, "REPORTS.VIEW")).as("%s, report viewer", m).isFalse();
                } else if (AnnotatedElementUtils.hasAnnotation(m, PostMapping.class)
                        || AnnotatedElementUtils.hasAnnotation(m, PutMapping.class)
                        || AnnotatedElementUtils.hasAnnotation(m, DeleteMapping.class)) {
                    writes++;
                    assertThat(allows(m, false, "SETTINGS.VIEW")).as("%s, settings viewer", m).isFalse();
                }
            }
        }
        // getAll + getById on each, plus the degree/major query on years and the degree/agency query on amounts.
        assertThat(reads).isEqualTo(10);
        assertThat(writes).isEqualTo(12);
    }

    @Test
    @DisplayName("R5-08 not widened: the tracer network stays student records; the recipient list stays finance")
    void keptNarrow() throws Exception {
        Method network = StudentTracerController.class.getMethod("getNetworkGraph", TracerFilterDto.class);
        Method options = StudentTracerController.class.getMethod("getFilterOptions", Integer.class);
        for (Method m : new Method[] {network, options}) {
            assertThat(allows(m, true, "REPORTS.VIEW", "REPORT_OTHER.VIEW")).as("%s, report viewer", m).isFalse();
            assertThat(allows(m, true, "STUDENTS.VIEW")).as("%s, student viewer", m).isTrue();
        }
        Method recipients = LookupController.class.getMethod("getAllReceivedWhoms");
        assertThat(allows(recipients, true, "FINANCE_ARCH.VIEW")).isFalse();
        assertThat(allows(recipients, true, "FINANCE.VIEW")).isTrue();
    }

    private static String[] components(Class<? extends Record> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toArray(String[]::new);
    }

    /** Whether a caller holding {@code held} passes the endpoint's gate. */
    private static boolean allows(Method m, boolean getRequest, String... held) {
        PreAuthorize gate = AnnotatedElementUtils.findMergedAnnotation(m, PreAuthorize.class);
        if (gate == null) gate = AnnotatedElementUtils.findMergedAnnotation(m.getDeclaringClass(), PreAuthorize.class);
        assertThat(gate).as("%s has a gate", m).isNotNull();
        StandardEvaluationContext context = new StandardEvaluationContext(new Caller(Set.of(held)));
        context.setBeanResolver((ctx, name) -> {
            if ("httpMethodSecurity".equals(name)) return new HttpMethod(getRequest);
            throw new AccessException("no bean " + name);
        });
        return Boolean.TRUE.equals(new SpelExpressionParser().parseExpression(gate.value()).getValue(context, Boolean.class));
    }

    /** The methods Spring Security's expression root offers, over a fixed set of codes. */
    public static final class Caller {
        private final Set<String> held;

        Caller(Set<String> held) {
            this.held = held;
        }

        public boolean hasAuthority(String code) {
            return held.contains(code);
        }

        public boolean hasAnyAuthority(String... codes) {
            return Arrays.stream(codes).anyMatch(held::contains);
        }

        public boolean hasRole(String role) {
            return held.contains("ROLE_" + role);
        }

        public boolean hasAnyRole(String... roles) {
            return Arrays.stream(roles).anyMatch(this::hasRole);
        }

        public boolean isAuthenticated() {
            return true;
        }
    }

    /** Stands in for the {@code httpMethodSecurity} bean. */
    public static final class HttpMethod {
        private final boolean get;

        HttpMethod(boolean get) {
            this.get = get;
        }

        public boolean isGetRequest() {
            return get;
        }
    }
}
