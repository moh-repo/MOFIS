package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.tenant.TenantContext;
import com.dilaw.mohe.model.PaymentAuth;
import com.dilaw.mohe.repository.PaymentAuthRepository;
import com.dilaw.mohe.scope.AsUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code POST /paymentAuth/{id}/mark-paid} and the DRAFT lock, through the controller
 * (review F6; plan W4-10).
 *
 * <p>Marking a payment paid is the same act as releasing it, so it carries the same
 * permission — {@code PAYMENT_AUTH.APPROVE} alone. The class-level gate admits
 * {@code FINANCE.VIEW}, and the write gate {@code PAYMENT_AUTH.EDIT} and
 * {@code FINANCE.EDIT}; none of those may record money as paid out. The status rules
 * themselves are in {@code PaymentAuthDraftLockTest}; this is the gate and what
 * reaches the table.
 *
 * <p>The rows are written inside a transaction the test rolls back itself, under the
 * SYSTEM scope set before the transaction borrows its connection, as in
 * {@code PaymentAuthBulkAuthorizeTest}. Nothing survives in {@code PAYMENT_AUTH}.
 */
@Tag("integration")
@SpringBootTest
class PaymentAuthMarkPaidTest {

    private static final int NO_SUCH_ID = -424242;

    @Autowired private PaymentAuthController controller;
    @Autowired private PaymentAuthRepository repository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager txManager;

    private <T> T rolledBack(Supplier<T> body) {
        return TenantContext.runAsSystem(() -> new TransactionTemplate(txManager).execute(status -> {
            status.setRollbackOnly();
            return body.get();
        }));
    }

    private Integer fixture(String authStatus) {
        Integer studentId = jdbc.queryForObject(
                "SELECT TOP 1 STUDENT_ID FROM MOHECORE.STUDENT_DATA WHERE STUDY_COUNTRY_IN IS NOT NULL ORDER BY STUDENT_ID",
                Integer.class);
        PaymentAuth row = new PaymentAuth();
        row.setStudentId(studentId);
        row.setAuthStatus(authStatus);
        row.setAuthDate(LocalDate.of(2026, 9, 1));
        row.setOriginalAmount(BigDecimal.TEN);
        row.setNetAmount(BigDecimal.TEN);
        row.setAuthorizedBy("DRAFT".equals(authStatus) ? null : AsUser.ADMIN);
        row.setAuthMidate(Instant.now());
        row.setPaymentPurpose("PaymentAuthMarkPaidTest — rolled back");
        return repository.saveAndFlush(row).getId();
    }

    private String storedStatus(Integer id) {
        return jdbc.queryForObject("SELECT AUTH_STATUS FROM MOHECORE.PAYMENT_AUTH WHERE PAYMENT_AUTH_ID = ?", String.class, id);
    }

    @Test
    @DisplayName("recording a payment is not paying it out: VIEW, EDIT and FINANCE.EDIT cannot mark it paid")
    void onlyApproveMayMarkPaid() {
        for (List<String> authorities : List.of(
                List.of("FINANCE.VIEW", "PAYMENT_AUTH.VIEW"),
                List.of("PAYMENT_AUTH.ADD", "PAYMENT_AUTH.EDIT", "PAYMENT_AUTH.DELETE"),
                List.of("FINANCE.EDIT"),
                List.of("SETTINGS.EDIT", "UMS_ADMIN.VIEW"))) {
            assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.ADMIN, authorities,
                    () -> controller.markPaid(NO_SUCH_ID)))
                    .as("mark-paid with only %s", authorities)
                    .isInstanceOf(AccessDeniedException.class);
        }

        // Positive control, and nothing is written: past the gate the unknown id is a 404.
        assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.ADMIN, List.of("PAYMENT_AUTH.APPROVE"),
                () -> controller.markPaid(NO_SUCH_ID)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("an APPROVE holder marks a released payment paid, and the table says PAID")
    void approvedIsMarkedPaid() {
        String stored = rolledBack(() -> {
            Integer id = fixture("APPROVED");

            PaymentAuth answer = AsUser.callWithAuthorities(AsUser.ADMIN, List.of("PAYMENT_AUTH.APPROVE"),
                    () -> controller.markPaid(id)).getBody();
            assertThat(answer).isNotNull();
            assertThat(answer.getAuthStatus()).isEqualTo("PAID");

            repository.flush();
            return storedStatus(id);
        });

        assertThat(stored).isEqualTo("PAID");
    }

    @Test
    @DisplayName("a draft nobody released cannot be marked paid: 409, and it is still a draft")
    void draftIsNotMarkedPaid() {
        String stored = rolledBack(() -> {
            Integer id = fixture("DRAFT");

            assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.ADMIN, List.of("PAYMENT_AUTH.APPROVE"),
                    () -> controller.markPaid(id)))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.CONFLICT);

            repository.flush();
            return storedStatus(id);
        });

        assertThat(stored).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("a released voucher is neither edited nor deleted through the API: 409 both times, the row intact")
    void releasedVoucherIsLocked() {
        List<String> writer = List.of("PAYMENT_AUTH.EDIT", "PAYMENT_AUTH.DELETE");

        Object[] after = rolledBack(() -> {
            Integer id = fixture("APPROVED");
            PaymentAuth edit = new PaymentAuth();
            edit.setStudentId(repository.findById(id).orElseThrow().getStudentId());
            edit.setAuthDate(LocalDate.of(2026, 9, 2));
            edit.setOriginalAmount(new BigDecimal("999"));

            assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.ADMIN, writer,
                    () -> controller.update(id, edit)))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.CONFLICT);
            assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.ADMIN, writer,
                    () -> controller.delete(id)))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.CONFLICT);

            repository.flush();
            return jdbc.queryForObject(
                    "SELECT COUNT(*), MAX(ORIGINAL_AMOUNT), MAX(AUTH_STATUS) FROM MOHECORE.PAYMENT_AUTH WHERE PAYMENT_AUTH_ID = ?",
                    (rs, n) -> new Object[] {rs.getInt(1), rs.getBigDecimal(2), rs.getString(3)}, id);
        });

        assertThat(after[0]).as("not deleted").isEqualTo(1);
        assertThat((BigDecimal) after[1]).as("not edited").isEqualByComparingTo(BigDecimal.TEN);
        assertThat(after[2]).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("positive control: a draft is deleted through the API")
    void draftIsDeleted() {
        Integer remaining = rolledBack(() -> {
            Integer id = fixture("DRAFT");

            AsUser.callWithAuthorities(AsUser.ADMIN, List.of("PAYMENT_AUTH.DELETE"), () -> controller.delete(id));

            repository.flush();
            return jdbc.queryForObject("SELECT COUNT(*) FROM MOHECORE.PAYMENT_AUTH WHERE PAYMENT_AUTH_ID = ?", Integer.class, id);
        });

        assertThat(remaining).isZero();
    }
}
