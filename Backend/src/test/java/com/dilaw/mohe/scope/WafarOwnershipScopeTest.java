package com.dilaw.mohe.scope;

import com.dilaw.mohe.approval.model.RequestType;
import com.dilaw.mohe.approval.service.ApprovalIntegrationService;
import com.dilaw.mohe.config.tenant.TenantContext;
import com.dilaw.mohe.model.StudentFinanceWafar;
import com.dilaw.mohe.service.WafarReconciliationService;
import com.dilaw.mohe.wafar.dto.WafarSpendingCreateDto;
import com.dilaw.mohe.wafar.dto.WafarSpendingDto;
import com.dilaw.mohe.wafar.service.WafarSpendingService;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Security plan N-04 (6 Sep 2026): each attaché has full control of its own Wafar
 * and nobody else writes it.
 *
 * <p>Before this pass every ministry account — the super admin, any
 * {@code IsMinistryUser}, and the all-country {@code admin} — could record,
 * edit, approve and cancel any attaché's spending: the write expressions took
 * {@code ROLE_ADMIN} and the service treated ministry scope as "every country".
 * Now the ministry reads and reports; the attaché's staff record and approve.
 * One exception, decision D-13(b): the super admin may still approve or reject
 * a pending spend, and every such use is written to {@code UMSCORE.UserActions}.
 *
 * <p>Writes one Jordanian spend as {@code jo_admin} and removes it, with its
 * audit rows, in a finally.
 */
@Tag("integration")
@SpringBootTest
class WafarOwnershipScopeTest {

    private static final int JORDAN = 1004;
    private static final String PROBE_REF = "N04-ownership-probe";

    @Autowired private WafarSpendingService spendingService;
    @Autowired private WafarReconciliationService reconciliationService;
    @Autowired private ApprovalIntegrationService approvalIntegrationService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager txManager;

    private static WafarSpendingCreateDto jordanOperatingExpense() {
        WafarSpendingCreateDto dto = new WafarSpendingCreateDto();
        dto.setCategoryId(3); // OPERATING_EXPENSE — no student, no fee side effect on approval
        dto.setAmount(new BigDecimal("10"));
        dto.setSpendingDate(LocalDate.now());
        dto.setBeneficiaryType("OTHER");
        dto.setPayeeNameEn("ownership probe");
        dto.setReferenceNumber(PROBE_REF);
        dto.setStudyCountryInId(JORDAN);
        return dto;
    }

    private static void assertForbidden(String what, ThrowingCallable call) {
        assertThatThrownBy(call)
                .as(what)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    private void removeProbe(Integer id) {
        TenantContext.runAsSystem(() -> {
            jdbc.update("DELETE FROM UMSCORE.UserActions WHERE Action LIKE 'WAFAR_SUPERADMIN_%' AND ResourceType = 'WAFAR_SPENDING' AND ResourceId = ?", id);
            jdbc.update("DELETE FROM AUDITLOG.AuditTrail WHERE TABLE_NAME = 'WAFAR_SPENDING' AND RECORD_ID = ?", String.valueOf(id));
            jdbc.update("DELETE FROM MOHECORE.WAFAR_SPENDING WHERE SPENDING_ID = ?", id);
            return null;
        });
    }

    @Test
    @DisplayName("ministry accounts cannot record a spend against any attaché")
    void ministryCannotRecord() {
        assertForbidden("super admin recording", () -> AsUser.call(AsUser.SUPERADMIN, () -> spendingService.create(jordanOperatingExpense())));
        assertForbidden("all-country admin recording", () -> AsUser.call(AsUser.ADMIN, () -> spendingService.create(jordanOperatingExpense())));

        Integer written = TenantContext.runAsSystem(() -> jdbc.queryForObject(
                "SELECT COUNT(*) FROM MOHECORE.WAFAR_SPENDING WHERE REFERENCE_NUMBER = ?", Integer.class, PROBE_REF));
        assertThat(written).as("the refusal happened before anything was written").isZero();
    }

    @Test
    @DisplayName("the attaché records; the ministry cannot edit or cancel; the super admin may approve, audited")
    void attacheOwnsTheSpend() {
        WafarSpendingDto created = AsUser.call(AsUser.JO_ADMIN, () -> spendingService.create(jordanOperatingExpense()));
        Integer id = created.getId();
        assertThat(created.getStudyCountryInId()).isEqualTo(JORDAN);
        assertThat(created.getStatus()).isEqualTo("PENDING");

        try {
            WafarSpendingCreateDto edit = jordanOperatingExpense();
            edit.setAmount(new BigDecimal("11"));
            assertForbidden("admin editing Jordan's spend", () -> AsUser.call(AsUser.ADMIN, () -> spendingService.update(id, edit)));
            assertForbidden("admin cancelling Jordan's spend", () -> AsUser.call(AsUser.ADMIN, () -> { spendingService.cancel(id); return null; }));
            assertForbidden("super admin cancelling Jordan's spend", () -> AsUser.call(AsUser.SUPERADMIN, () -> { spendingService.cancel(id); return null; }));
            assertForbidden("the maker approving their own spend", () -> AsUser.call(AsUser.JO_ADMIN, () -> spendingService.approve(id)));

            // Positive control for the attaché: its own staff may still edit it.
            WafarSpendingDto edited = AsUser.call(AsUser.JO_ADMIN, () -> spendingService.update(id, edit));
            assertThat(edited.getAmount()).isEqualByComparingTo("11");

            Integer auditBefore = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM UMSCORE.UserActions WHERE Action = 'WAFAR_SUPERADMIN_APPROVE' AND ResourceId = ?",
                    Integer.class, id);
            WafarSpendingDto approved = AsUser.call(AsUser.SUPERADMIN, () -> spendingService.approve(id));
            assertThat(approved.getStatus()).isEqualTo("APPROVED");
            assertThat(approved.getApprovedBy()).isEqualTo((int) AsUser.SUPERADMIN);
            Integer auditAfter = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM UMSCORE.UserActions WHERE Action = 'WAFAR_SUPERADMIN_APPROVE' AND ResourceId = ?",
                    Integer.class, id);
            assertThat(auditAfter)
                    .as("D-13(b): the escape hatch exists, and every use of it leaves an audit row")
                    .isEqualTo(auditBefore + 1);
        } finally {
            removeProbe(id);
        }
    }

    @Test
    @DisplayName("ministry accounts cannot run the Wafar reconciliation; the super admin may, through the hatch")
    void ministryCannotReconcile() {
        assertForbidden("admin running reconciliation", () -> AsUser.call(AsUser.ADMIN, () -> reconciliationService.runReconciliation()));
        // D-13(b) extended 23 Sep 2026 (live QA M2): the super admin runs the comparison,
        // and the N-04.d write key is raised for the transaction — or the block predicate
        // on WAFAR_RECONCILIATION would refuse the rows. Rolled back: the run is a real
        // comparison over the development data and this test must not refresh it.
        Integer discrepancies = new TransactionTemplate(txManager).execute(status -> {
            status.setRollbackOnly();
            return AsUser.call(AsUser.SUPERADMIN, () -> reconciliationService.runReconciliation());
        });
        assertThat(discrepancies).as("the super admin's run completes").isNotNull().isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("ministry accounts cannot raise a Wafar finance change for a student")
    void ministryCannotRaiseWafarFinanceRequest() {
        Integer studentId = TenantContext.runAsSystem(() -> {
            var ids = jdbc.queryForList("SELECT TOP 1 STUDENT_ID FROM MOHECORE.STUDENT_FINANCE_WAFAR", Integer.class);
            return ids.isEmpty() ? null : ids.get(0);
        });
        assumeTrue(studentId != null, "no student finance Wafar rows");

        StudentFinanceWafar row = new StudentFinanceWafar();
        row.setId(studentId);
        // DELETE skips the finance validation, so the ownership refusal is the first thing reached.
        assertForbidden("admin raising a Wafar deletion", () -> AsUser.call(AsUser.ADMIN,
                () -> approvalIntegrationService.createStudentFinanceWafarRequest(RequestType.DELETE, row, row, AsUser.ADMIN)));
        assertForbidden("super admin raising a Wafar deletion", () -> AsUser.call(AsUser.SUPERADMIN,
                () -> approvalIntegrationService.createStudentFinanceWafarRequest(RequestType.DELETE, row, row, AsUser.SUPERADMIN)));
    }
}
