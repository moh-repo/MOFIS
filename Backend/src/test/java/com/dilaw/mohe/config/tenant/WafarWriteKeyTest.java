package com.dilaw.mohe.config.tenant;

import com.dilaw.mohe.scope.AsUser;
import com.dilaw.mohe.wafar.dto.WafarSpendingCreateDto;
import com.dilaw.mohe.wafar.dto.WafarSpendingDto;
import com.dilaw.mohe.wafar.service.WafarSpendingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * N-04.d: the database itself refuses a Wafar write from a ministry connection
 * (V095), and the two legitimate exceptions raise the key for one transaction.
 *
 * <p>Raw JDBC on purpose: the point of the backstop is what happens when the
 * Java checks are not in the way. Writes one Jordanian spend as {@code jo_admin}
 * and removes it as system in a finally.
 */
@Tag("integration")
@SpringBootTest
class WafarWriteKeyTest {

    private static final int JORDAN = 1004;
    private static final String PROBE_REF = "N04d-write-key-probe";

    @Autowired private WafarSpendingService spendingService;
    @Autowired private WafarWriteKey wafarWriteKey;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager txManager;

    private static WafarSpendingCreateDto jordanOperatingExpense() {
        WafarSpendingCreateDto dto = new WafarSpendingCreateDto();
        dto.setCategoryId(3); // OPERATING_EXPENSE
        dto.setAmount(new BigDecimal("10"));
        dto.setSpendingDate(LocalDate.now());
        dto.setBeneficiaryType("OTHER");
        dto.setPayeeNameEn("write key probe");
        dto.setReferenceNumber(PROBE_REF);
        dto.setStudyCountryInId(JORDAN);
        return dto;
    }

    private void removeProbe() {
        TenantContext.runAsSystem(() -> {
            jdbc.update("DELETE FROM AUDITLOG.AuditTrail WHERE TABLE_NAME = 'WAFAR_SPENDING' AND RECORD_ID IN "
                    + "(SELECT CAST(SPENDING_ID AS NVARCHAR(20)) FROM MOHECORE.WAFAR_SPENDING WHERE REFERENCE_NUMBER = ?)", PROBE_REF);
            jdbc.update("DELETE FROM MOHECORE.WAFAR_SPENDING WHERE REFERENCE_NUMBER = ?", PROBE_REF);
            return null;
        });
    }

    private BigDecimal amountOf(Integer id) {
        return TenantContext.runAsSystem(() -> jdbc.queryForObject(
                "SELECT AMOUNT FROM MOHECORE.WAFAR_SPENDING WHERE SPENDING_ID = ?", BigDecimal.class, id));
    }

    @Test
    @DisplayName("a ministry connection cannot update or delete a Wafar row, even with raw SQL; the attaché and system can")
    void ministryConnectionIsBlocked() {
        WafarSpendingDto created = AsUser.call(AsUser.JO_ADMIN, () -> spendingService.create(jordanOperatingExpense()));
        Integer id = created.getId();
        try {
            TenantContext.Scope ministry = TenantContext.Scope.of(true, List.of());
            assertThat(ministry.all()).isTrue();
            assertThat(ministry.wafarWrite()).as("a ministry scope sees everything and writes no Wafar").isFalse();

            assertThatThrownBy(() -> TenantContext.runAs(ministry, () ->
                    jdbc.update("UPDATE MOHECORE.WAFAR_SPENDING SET AMOUNT = 11 WHERE SPENDING_ID = ?", id)))
                    .as("update from a ministry connection")
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("block predicate");
            assertThatThrownBy(() -> TenantContext.runAs(ministry, () ->
                    jdbc.update("DELETE FROM MOHECORE.WAFAR_SPENDING WHERE SPENDING_ID = ?", id)))
                    .as("delete from a ministry connection")
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("block predicate");
            assertThat(amountOf(id)).isEqualByComparingTo("10");

            // The attaché's own connection writes its own rows.
            TenantContext.Scope jordan = TenantContext.Scope.of(false, List.of(JORDAN));
            assertThat(jordan.wafarWrite()).isTrue();
            int updated = TenantContext.runAs(jordan, () ->
                    jdbc.update("UPDATE MOHECORE.WAFAR_SPENDING SET AMOUNT = 12 WHERE SPENDING_ID = ?", id));
            assertThat(updated).isEqualTo(1);
            assertThat(amountOf(id)).isEqualByComparingTo("12");

            // Another attaché's connection cannot see the row, let alone write it.
            int fromSaudi = TenantContext.runAs(TenantContext.Scope.of(false, List.of(1005)), () ->
                    jdbc.update("UPDATE MOHECORE.WAFAR_SPENDING SET AMOUNT = 13 WHERE SPENDING_ID = ?", id));
            assertThat(fromSaudi).isZero();
        } finally {
            removeProbe();
        }
    }

    @Test
    @DisplayName("the key raised inside a transaction lets that transaction write, and only that one")
    void elevationIsPerTransaction() {
        WafarSpendingDto created = AsUser.call(AsUser.JO_ADMIN, () -> spendingService.create(jordanOperatingExpense()));
        Integer id = created.getId();
        try {
            TenantContext.Scope ministry = TenantContext.Scope.of(true, List.of());
            TransactionTemplate tx = new TransactionTemplate(txManager);

            assertThatThrownBy(() -> wafarWriteKey.elevateCurrentTransaction("test"))
                    .as("outside a transaction there is no connection to raise the key on")
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("transaction");

            Integer updated = TenantContext.runAs(ministry, () -> tx.execute(status -> {
                wafarWriteKey.elevateCurrentTransaction("WafarWriteKeyTest");
                return jdbc.update("UPDATE MOHECORE.WAFAR_SPENDING SET AMOUNT = 14 WHERE SPENDING_ID = ?", id);
            }));
            assertThat(updated).isEqualTo(1);
            assertThat(amountOf(id)).isEqualByComparingTo("14");

            // The next transaction on a ministry connection is blocked again.
            assertThatThrownBy(() -> TenantContext.runAs(ministry, () -> tx.execute(status ->
                    jdbc.update("UPDATE MOHECORE.WAFAR_SPENDING SET AMOUNT = 15 WHERE SPENDING_ID = ?", id))))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("block predicate");
            assertThat(amountOf(id)).isEqualByComparingTo("14");
        } finally {
            removeProbe();
        }
    }
}
