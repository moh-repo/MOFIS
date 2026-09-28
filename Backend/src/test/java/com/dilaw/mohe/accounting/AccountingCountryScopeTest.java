package com.dilaw.mohe.accounting;

import com.dilaw.mohe.config.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Country scope across FINCORE.
 *
 * <p>Country scoping is not incidental to this module: an attaché <em>is</em> a
 * country. A country officer who could read another attaché's advance balance
 * would be reading another country's budget.
 *
 * <p>V041 and V042 add their tables to {@code tenant_country_policy} with
 * {@code ALTER ... ADD} rather than the drop-and-recreate V032 uses, because the
 * policy already carried 69 predicates across MOHECORE and STUDENTCORE — several
 * with rules V037 chose deliberately — and rebuilding risked losing them.
 *
 * <p><b>Not {@code @Transactional}.</b> Enabling the policy and then reading
 * through it needs each read to borrow its own connection:
 * {@code TenantContextDataSource} stamps the scope at {@code getConnection()},
 * so a test transaction would fix the scope before the test body could change
 * it. Same reasoning as {@code StudentPortalLoginCountryTest}.
 */
@Tag("integration")
@SpringBootTest
class AccountingCountryScopeTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private com.dilaw.mohe.scope.DdlJdbc ddl;

    /**
     * The policy state before the test ran, restored afterwards.
     *
     * <p>Until the third revision pass (27 Aug 2026) the teardown forced the
     * policy OFF unconditionally, so every full-suite run switched row-level
     * security off on the development database — which is how V062 "enabled on
     * dev" turned out to be false a few hours later. The suite must leave the
     * database exactly as it found it.
     */
    private boolean policyWasEnabled;

    @BeforeEach
    void rememberPolicyState() {
        policyWasEnabled = policyEnabled() == 1;
    }

    @AfterEach
    void leaveNothingBehind() {
        TenantContext.clear();
        setPolicy(policyWasEnabled);
    }

    @Test
    @DisplayName("every FINCORE table that holds tenant data is under the policy")
    void allTenantTablesAreScoped() {
        List<String> outsidePolicy = jdbc.queryForList("""
                SELECT t.name FROM sys.tables t
                WHERE SCHEMA_NAME(t.schema_id) = 'FINCORE'
                  AND EXISTS (SELECT 1 FROM sys.columns c
                              WHERE c.object_id = t.object_id AND c.name = 'STUDY_COUNTRY_IN_ID')
                  AND NOT EXISTS (SELECT 1 FROM sys.security_predicates sp
                                  JOIN sys.security_policies p ON p.object_id = sp.object_id
                                  WHERE p.name = 'tenant_country_policy'
                                    AND sp.target_object_id = t.object_id)
                ORDER BY t.name
                """, String.class);

        assertThat(outsidePolicy)
                .as("a country column without a predicate is documentation, not enforcement")
                .isEmpty();
    }

    @Test
    @DisplayName("the tables that hold no tenant data are deliberately unscoped")
    void referenceTablesAreMinistryWide() {
        List<String> noCountryColumn = jdbc.queryForList("""
                SELECT t.name FROM sys.tables t
                WHERE SCHEMA_NAME(t.schema_id) = 'FINCORE'
                  AND COL_LENGTH('FINCORE.' + t.name, 'STUDY_COUNTRY_IN_ID') IS NULL
                ORDER BY t.name
                """, String.class);

        assertThat(noCountryColumn)
                .as("""
                    These are structure, not money: the segments and levels of the chart, the \
                    fee-type catalogue, the fiscal periods and the FX rates. Scoping them would \
                    mean a country officer losing the chart of accounts itself.""")
                .containsExactlyInAnyOrder(
                        "COA_LEVEL", "COA_SEGMENT", "FEE_TYPE", "FISCAL_PERIOD", "FX_RATE");
    }

    @Test
    @DisplayName("with the policy on, an officer sees only their own attaché's advance")
    void advancesAreFilteredByCountry() {
        Integer fiscalYear = jdbc.queryForObject(
                "SELECT FISCAL_YEAR_ID FROM FINCORE.FISCAL_YEAR WHERE YEAR_NO = 2026", Integer.class);
        try {
            TenantContext.runAsSystem(() -> jdbc.update("""
                    INSERT INTO FINCORE.ATTACHE_ADVANCE_BALANCE
                        (FISCAL_YEAR_ID, STUDY_COUNTRY_IN_ID, CURRENCY_ID, TRANSFERRED)
                    VALUES (?, 1, 1, 5000.00), (?, 1000, 1, 7000.00)
                    """, fiscalYear, fiscalYear));

            setPolicy(true);

            // Counted within this test's own fiscal year. Counting the whole
            // table would make the assertion depend on whatever else the
            // database happens to hold — V045 derives real balances for 2024
            // and 2025, and this test broke the moment it did.
            assertThat(countAdvances(TenantContext.Scope.SYSTEM, fiscalYear))
                    .as("a ministry-wide user sees every attaché")
                    .isEqualTo(2);
            assertThat(countAdvances(TenantContext.Scope.of(false, List.of(1)), fiscalYear))
                    .as("an officer scoped to country 1 sees only that attaché's advance")
                    .isEqualTo(1);
            assertThat(countAdvances(TenantContext.Scope.of(false, List.of(999999)), fiscalYear))
                    .as("and an officer with no attaché here sees nothing — not another country's budget")
                    .isZero();
        } finally {
            setPolicy(false);
            TenantContext.runAsSystem(() ->
                    jdbc.update("DELETE FROM FINCORE.ATTACHE_ADVANCE_BALANCE WHERE FISCAL_YEAR_ID = ?", fiscalYear));
        }
    }

    @Test
    @DisplayName("the policy exists, is schema-bound, and its state is whatever the deployment chose")
    void policyStateIsADeploymentDecision() {
        // V031 shipped the policy OFF; V062 turns it ON. Which one a given database
        // is at is a deployment decision, so this test no longer asserts a state —
        // an earlier version asserted OFF and passed only because the tests before
        // it in this class had just switched it off.
        Integer schemaBound = jdbc.queryForObject(
                "SELECT CAST(is_schema_bound AS INT) FROM sys.security_policies WHERE name = 'tenant_country_policy'",
                Integer.class);
        assertThat(schemaBound).as("the policy must exist and be schema-bound").isEqualTo(1);
        assertThat(policyEnabled()).isIn(0, 1);
    }

    private int policyEnabled() {
        Integer enabled = jdbc.queryForObject(
                "SELECT CAST(is_enabled AS INT) FROM sys.security_policies WHERE name = 'tenant_country_policy'",
                Integer.class);
        return enabled == null ? 0 : enabled;
    }

    private int countAdvances(TenantContext.Scope scope, Integer fiscalYearId) {
        return TenantContext.runAs(scope, () -> jdbc.queryForObject(
                "SELECT COUNT(*) FROM FINCORE.ATTACHE_ADVANCE_BALANCE WHERE FISCAL_YEAR_ID = ?",
                Integer.class, fiscalYearId));
    }

    /** Always put back to its original state in {@link #leaveNothingBehind()}. */
    private void setPolicy(boolean on) {
        // DDL: through the migrations' login, since mohe_app is denied ALTER ANY SECURITY POLICY (V090).
        ddl.setTenantPolicy(on);
    }
}
