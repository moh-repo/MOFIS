package com.dilaw.mohe.wafar;

import com.dilaw.mohe.approval.model.ApprovalRequest;
import com.dilaw.mohe.approval.service.ApprovalWorkflowService;
import com.dilaw.mohe.scope.AsUser;
import com.dilaw.mohe.wafar.dto.WafarCorrectionRequest;
import com.dilaw.mohe.wafar.service.WafarSpendingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * N-04.e: the ministry corrects an attaché's Wafar row by asking, not by
 * writing. The request is routed to the attaché's administrator; a ministry
 * account cannot approve it; the approved change is applied as the approver.
 * Inside a rolled-back transaction, on a Jordan row planted for the test.
 */
@Tag("integration")
@SpringBootTest
@Transactional
class WafarCorrectionTest {

    @Autowired private WafarSpendingService spending;
    @Autowired private ApprovalWorkflowService workflow;
    @Autowired private JdbcTemplate jdbc;
    /** The executor changes the row through JPA; the raw reads below need it flushed first. */
    @Autowired private jakarta.persistence.EntityManager em;

    private static final int JORDAN = 1004;
    private int rowId;

    @BeforeEach
    void plantAJordanRow() {
        Integer category = jdbc.queryForObject("SELECT TOP 1 CATEGORY_ID FROM MOHECORE.WAFAR_SPENDING_CATEGORY ORDER BY CATEGORY_ID", Integer.class);
        jdbc.update("""
                INSERT INTO MOHECORE.WAFAR_SPENDING (CATEGORY_ID, AMOUNT, SPENDING_DATE, DESCRIPTION_EN, STUDY_COUNTRY_IN_ID,
                                                    STATUS, CREATED_BY, CREATED_AT)
                VALUES (?, 1500.00, '2026-08-01', 'N-04.e probe', ?, 'PENDING', ?, SYSDATETIME())
                """, category, JORDAN, 14L);
        rowId = jdbc.queryForObject("SELECT MAX(SPENDING_ID) FROM MOHECORE.WAFAR_SPENDING WHERE DESCRIPTION_EN = 'N-04.e probe'", Integer.class);
    }

    private BigDecimal amount() {
        return jdbc.queryForObject("SELECT AMOUNT FROM MOHECORE.WAFAR_SPENDING WHERE SPENDING_ID = ?", BigDecimal.class, rowId);
    }

    @Test
    @DisplayName("ministry asks, the attaché's administrator approves, the row changes as the approver")
    void correctionPath() {
        WafarCorrectionRequest fix = new WafarCorrectionRequest(new BigDecimal("1050.00"), null, null, null,
                "corrected after the bank statement", null, "duplicate line on the statement");

        // Jordan's own staff do not use this path.
        assertThatThrownBy(() -> AsUser.call(AsUser.JO_ADMIN, () -> spending.requestCorrection(rowId, fix)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("ministry");

        ApprovalRequest request = AsUser.call(AsUser.ADMIN, () -> spending.requestCorrection(rowId, fix));
        assertThat(request.getEntityType()).isEqualTo("WAFAR_CORRECTION");
        assertThat(request.getCountryId()).isEqualTo(JORDAN);
        assertThat(amount()).as("nothing changed yet").isEqualByComparingTo("1500.00");

        // A ministry staff account may not approve it (N-04: the attaché holds the money).
        assertThatThrownBy(() -> AsUser.call(AsUser.ADMIN, () -> workflow.approveStep(request.getId(), null, "ministry signs")))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(amount()).isEqualByComparingTo("1500.00");

        AsUser.call(AsUser.JO_ADMIN, () -> workflow.approveStep(request.getId(), null, "checked against the statement"));
        em.flush();
        assertThat(amount()).isEqualByComparingTo("1050.00");
        assertThat(jdbc.queryForObject("SELECT NOTES FROM MOHECORE.WAFAR_SPENDING WHERE SPENDING_ID = ?", String.class, rowId))
                .isEqualTo("corrected after the bank statement");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM UMSCORE.UserActions WHERE [Action] = 'WAFAR_CORRECTED' AND ResourceId = ?",
                Integer.class, rowId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM UMSCORE.UserActions WHERE [Action] = 'WAFAR_CORRECTION_REQUESTED' AND UserId = ?",
                Integer.class, AsUser.ADMIN)).isEqualTo(1);
    }

    @Test
    @DisplayName("a correction that changes nothing, or cancels an approved row, is refused")
    void refusals() {
        assertThatThrownBy(() -> AsUser.call(AsUser.ADMIN, () -> spending.requestCorrection(rowId,
                new WafarCorrectionRequest(null, null, null, null, null, null, "nothing"))))
                .hasMessageContaining("changes nothing");
        jdbc.update("UPDATE MOHECORE.WAFAR_SPENDING SET STATUS = 'APPROVED' WHERE SPENDING_ID = ?", rowId);
        assertThatThrownBy(() -> AsUser.call(AsUser.ADMIN, () -> spending.requestCorrection(rowId,
                new WafarCorrectionRequest(null, null, null, null, null, true, "cancel it"))))
                .hasMessageContaining("PENDING");
    }
}
