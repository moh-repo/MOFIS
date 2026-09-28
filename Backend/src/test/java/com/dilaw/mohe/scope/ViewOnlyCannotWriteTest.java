package com.dilaw.mohe.scope;

import com.dilaw.mohe.approval.controler.ApprovalConfigController;
import com.dilaw.mohe.controler.BackupController;
import com.dilaw.mohe.controler.StudentFamilyController;
import com.dilaw.mohe.controler.StudentFinanceWafarController;
import com.dilaw.mohe.controler.StudentInfoRequestController;
import com.dilaw.mohe.controler.StudentQuickEditController;
import com.dilaw.mohe.controler.StudentTotalReportAllController;
import com.dilaw.mohe.controler.StudentTracerController;
import com.dilaw.mohe.controler.StudyFinReceivedWhomController;
import com.dilaw.mohe.controler.StudyWafarTypeController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The §14 leads of the round-4 QA report, "save actions by view-only roles"
 * (FIX-PLAN ESC, 25 Sep 2026): each write is refused to an account holding only
 * the read codes its controller's class-level gate accepts, and gets past the
 * gate with the write code. Every id is one that does not exist, so nothing is
 * written. {@code WriteEndpointAuthorityArchTest} covers the same rule for every
 * controller without a database; this proves it through the real proxies.
 */
@Tag("integration")
@SpringBootTest
class ViewOnlyCannotWriteTest {

    private static final int NO_SUCH_ID = -424242;

    @Autowired private ApprovalConfigController approvalConfigs;
    @Autowired private StudentFamilyController family;
    @Autowired private StudyWafarTypeController wafarTypes;
    @Autowired private StudyFinReceivedWhomController receivedBy;
    @Autowired private StudentQuickEditController quickEdit;
    @Autowired private BackupController backups;
    @Autowired private StudentTracerController tracer;
    @Autowired private StudentTotalReportAllController totalReport;
    @Autowired private StudentFinanceWafarController financeWafar;
    @Autowired private StudentInfoRequestController infoRequests;

    private static void assertDenied(String what, List<String> authorities, Supplier<?> call) {
        assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.ADMIN, authorities, call))
                .as("%s with only %s", what, authorities)
                .isInstanceOf(AccessDeniedException.class);
    }

    private static void assertPermitted(String what, List<String> authorities, Supplier<?> call) {
        assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.ADMIN, authorities, () -> {
            Object r = call.get();
            throw new IllegalStateException("past authorization: " + r);
        }))
                .as("positive control: %s with %s must get past authorization", what, authorities)
                .isNotInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("approval rules: APPROVALS.VIEW cannot remove a level; APP_CONFIG.EDIT can")
    void approvalRules() {
        assertDenied("approval level delete", List.of("APPROVALS.VIEW", "APP_PENDING.VIEW"),
                () -> approvalConfigs.deleteLevel((long) NO_SUCH_ID));
        assertDenied("approval config deactivate", List.of("APPROVALS.VIEW"),
                () -> approvalConfigs.deactivateConfig((long) NO_SUCH_ID));
        assertPermitted("approval level delete", List.of("APP_CONFIG.EDIT"),
                () -> approvalConfigs.deleteLevel((long) NO_SUCH_ID));
    }

    @Test
    @DisplayName("family and death case: STUDENTS.VIEW cannot write; STUDENTS.EDIT can")
    void family() {
        List<String> view = List.of("STUDENTS.VIEW", "STUDENT_HISTORY.VIEW", "STUDENT_QUICK_EDIT.VIEW");
        assertDenied("parent delete", view, () -> family.deleteParent(NO_SUCH_ID, NO_SUCH_ID));
        assertDenied("relative delete", view, () -> family.deleteRelative(NO_SUCH_ID, NO_SUCH_ID));
        assertDenied("death case close", view, () -> family.closeDeathCase(NO_SUCH_ID));
        assertPermitted("parent delete", List.of("STUDENTS.EDIT"), () -> family.deleteParent(NO_SUCH_ID, NO_SUCH_ID));
    }

    @Test
    @DisplayName("Wafar types: WAFAR.* cannot write; SETTINGS.EDIT can (D16)")
    void wafarTypes() {
        assertDenied("Wafar type delete", List.of("WAFAR.VIEW", "WAFAR.ADD", "WAFAR.EDIT"),
                () -> wafarTypes.delete(NO_SUCH_ID));
        assertPermitted("Wafar type delete", List.of("SETTINGS.EDIT"), () -> wafarTypes.delete(NO_SUCH_ID));
    }

    @Test
    @DisplayName("received-by: FINANCE.VIEW/EXPORT cannot write or remove documents; FINANCE.EDIT can")
    void receivedBy() {
        List<String> view = List.of("FINANCE.VIEW", "FINANCE.EXPORT");
        assertDenied("recipient delete", view, () -> receivedBy.delete(NO_SUCH_ID));
        assertDenied("recipient document delete", view, () -> receivedBy.deleteDocument(NO_SUCH_ID));
        assertPermitted("recipient document delete", List.of("FINANCE.EDIT"), () -> receivedBy.deleteDocument(NO_SUCH_ID));
    }

    @Test
    @DisplayName("quick edit: STUDENT_QUICK_EDIT.VIEW cannot save")
    void quickEdit() {
        assertDenied("quick edit save", List.of("STUDENT_QUICK_EDIT.VIEW", "STUDENTS.VIEW"),
                () -> quickEdit.update(NO_SUCH_ID, new com.dilaw.mohe.dto.StudentQuickEditDto()));
    }

    @Test
    @DisplayName("backup download: SETTINGS.VIEW and SETTINGS.EDIT are refused; the super admin tier only (D17)")
    void backupDownload() {
        assertDenied("backup download", List.of("SETTINGS.VIEW"), () -> backups.downloadBackup("none.bak"));
        assertDenied("backup download", List.of("SETTINGS.EDIT"), () -> backups.downloadBackup("none.bak"));
        assertPermitted("backup download", List.of("ROLE_SUPER_ADMIN"), () -> backups.downloadBackup("none.bak"));
    }

    @Test
    @DisplayName("tracer, total-report table, Wafar deposits: read codes cannot write")
    void otherReadGatedWrites() {
        assertDenied("tracer employment delete", List.of("STUDENTS.VIEW"), () -> tracer.deleteEmployment(NO_SUCH_ID));
        assertDenied("total report delete", List.of("REPORT_ACADEMIC.VIEW", "REPORTS.VIEW"), () -> {
            totalReport.delete(String.valueOf(NO_SUCH_ID));
            return null;
        });
        assertDenied("Wafar deposit delete", List.of("WAFAR.VIEW", "WAFAR.EXPORT"), () -> financeWafar.delete(NO_SUCH_ID));
    }

    @Test
    @DisplayName("information requests: STUDENTS.VIEW cannot raise or cancel one (H3)")
    void infoRequests() {
        MockHttpServletRequest req = new MockHttpServletRequest("PUT", "/mohe/api/v1/student-info-requests/" + NO_SUCH_ID);
        assertDenied("info request cancel", List.of("STUDENTS.VIEW", "STUDENT_INFO_REQUESTS.VIEW"),
                () -> infoRequests.cancel(NO_SUCH_ID, req));
        assertDenied("info request create", List.of("STUDENTS.VIEW"),
                () -> infoRequests.create(Map.of("target", Map.of("studentIds", List.of(NO_SUCH_ID))), req));
        assertPermitted("info request cancel", List.of("STUDENT_INFO_REQUESTS.EDIT"),
                () -> infoRequests.cancel(NO_SUCH_ID, req));
    }
}
