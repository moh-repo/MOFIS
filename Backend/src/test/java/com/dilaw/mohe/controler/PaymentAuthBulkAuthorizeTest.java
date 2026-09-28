package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.tenant.TenantContext;
import com.dilaw.mohe.dto.PaymentAuthBulkAuthorizeRequest;
import com.dilaw.mohe.model.PaymentAuth;
import com.dilaw.mohe.repository.PaymentAuthRepository;
import com.dilaw.mohe.scope.AsUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Releasing payments ({@code POST /paymentAuth/bulk-authorize}) — plan item 1.2.
 *
 * <p>Two things were wrong. The class-level gate admitted {@code FINANCE.VIEW},
 * so a read-only finance role could release money; and the service computed
 * who released it, put that in the response, and never wrote it. The
 * {@code AUTHORIZED_BY} column exists since V065.
 *
 * <p>The write-side test runs inside a transaction it rolls back itself, under
 * the SYSTEM tenant scope set <em>before</em> the transaction borrows its
 * connection (the RLS session context is stamped at borrow time). Nothing
 * survives in {@code MOHECORE.PAYMENT_AUTH}. The per-row failure semantics
 * ({@code String.valueOf} on a null message, scope refusals propagating) are
 * in {@code PaymentAuthBulkAuthorizeRowFailureTest}, where the repository can
 * be made to fail on cue.
 */
@Tag("integration")
@SpringBootTest
class PaymentAuthBulkAuthorizeTest {

    private static final int NO_SUCH_ID = -424242;

    @Autowired private PaymentAuthController controller;
    @Autowired private PaymentAuthRepository repository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager txManager;

    @Test
    @DisplayName("a VIEW-only finance role cannot release payments; EDIT cannot either")
    void viewOnlyIsRefused() {
        PaymentAuthBulkAuthorizeRequest release = new PaymentAuthBulkAuthorizeRequest(List.of(NO_SUCH_ID));
        assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.ADMIN,
                List.of("FINANCE.VIEW", "PAYMENT_AUTH.VIEW", "PAYMENT_AUTH.EXPORT"),
                () -> controller.bulkAuthorize(release)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.ADMIN,
                List.of("FINANCE.EDIT", "PAYMENT_AUTH.EDIT"),
                () -> controller.bulkAuthorize(release)))
                .as("recording a payment and releasing it are different acts")
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("an APPROVE holder releases a draft, and the row records who did it")
    void approveHolderReleasesAndIsRecorded() {
        TenantContext.runAsSystem(() -> new TransactionTemplate(txManager).execute(status -> {
            status.setRollbackOnly();

            Integer studentId = jdbc.queryForObject(
                    "SELECT TOP 1 STUDENT_ID FROM MOHECORE.STUDENT_DATA WHERE STUDY_COUNTRY_IN IS NOT NULL ORDER BY STUDENT_ID",
                    Integer.class);
            PaymentAuth draft = new PaymentAuth();
            draft.setStudentId(studentId);
            draft.setAuthStatus("DRAFT");
            draft.setOriginalAmount(BigDecimal.TEN);
            draft.setNetAmount(BigDecimal.TEN);
            draft.setAuthMidate(Instant.now()); // NOT NULL; PaymentAuthService.create stamps it, this fixture must too
            draft.setPaymentPurpose("PaymentAuthBulkAuthorizeTest — rolled back");
            Integer id = repository.saveAndFlush(draft).getId();

            Map<String, Object> result = AsUser.callWithAuthorities(AsUser.ADMIN, List.of("PAYMENT_AUTH.APPROVE"),
                    () -> controller.bulkAuthorize(new PaymentAuthBulkAuthorizeRequest(List.of(id, NO_SUCH_ID)))).getBody();

            assertThat(result).isNotNull();
            assertThat(result.get("authorized")).isEqualTo(1);
            assertThat(result.get("authorizedBy")).isEqualTo(AsUser.ADMIN);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> failed = (List<Map<String, Object>>) result.get("failed");
            assertThat(failed).as("the unknown id is reported, not thrown, and does not stop the batch")
                    .extracting(m -> m.get("id")).containsExactly(NO_SUCH_ID);

            repository.flush();
            PaymentAuth stored = repository.findById(id).orElseThrow();
            assertThat(stored.getAuthStatus()).isEqualTo("APPROVED");
            assertThat(stored.getAuthorizedBy()).as("AUTHORIZED_BY is persisted, not just returned")
                    .isEqualTo(AsUser.ADMIN);
            return null;
        }));
    }
}
