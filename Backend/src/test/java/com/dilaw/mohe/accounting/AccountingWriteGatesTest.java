package com.dilaw.mohe.accounting;

import com.dilaw.mohe.accounting.controller.BudgetAppropriationController;
import com.dilaw.mohe.accounting.controller.ChartOfAccountsController;
import com.dilaw.mohe.accounting.controller.FundChainController;
import com.dilaw.mohe.accounting.controller.FxRateController;
import com.dilaw.mohe.accounting.controller.PaymentBatchController;
import com.dilaw.mohe.config.MoheSecurityExpressions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Who may write the ministry's accounting (round-5 QA). Each gate is evaluated as Spring
 * evaluates it, against the codes the seeded roles hold on the development database
 * (read 27 Sep 2026).
 *
 * <p>R5-02, decision D23: V145 took ALLOCATION.CREATE and PAYMENT_BATCH.CREATE off the
 * admin roles, but both create gates also accepted ACCOUNTING.EDIT, which MINISTRY_ADMIN,
 * COUNTRY_ADMIN, Admin, Super Admin and SUPER_ADMIN hold, so they kept creating (QA made
 * allocations 34 and 35). The super admin keeps creating (D-2): the IsSuperAdmin flag
 * carries every active code, the maker codes included.
 */
class AccountingWriteGatesTest {

    private static final Set<String> COUNTRY_ADMIN = Set.of("ACCOUNTING.VIEW", "ACCOUNTING.EDIT");
    private static final Set<String> MINISTRY_ADMIN = Set.of("ACCOUNTING.VIEW", "ACCOUNTING.EDIT", "COA.EDIT", "BUDGET.EDIT");
    /** The Admin and Super Admin roles; V146 gave them BUDGET.EDIT. */
    private static final Set<String> ADMIN_ROLE = Set.of("ACCOUNTING.EDIT", "COA.EDIT", "BUDGET.EDIT");
    private static final Set<String> FINANCE_OFFICER = Set.of("FINANCE.VIEW", "ALLOCATION.CREATE", "PAYMENT_BATCH.CREATE");
    /** A sample of the super admin's codes: every active code, maker codes included. */
    private static final Set<String> SUPER_ADMIN = Set.of("ACCOUNTING.EDIT", "COA.EDIT", "BUDGET.EDIT",
            "ALLOCATION.CREATE", "ALLOCATION.APPROVE", "PAYMENT_BATCH.CREATE", "PAYMENT_BATCH.APPROVE");

    @Test
    @DisplayName("R5-02: only a holder of the maker code creates an allocation or a payment batch")
    void makerCodesOnly() {
        for (String gate : new String[] {MoheSecurityExpressions.ALLOCATION_CREATE, MoheSecurityExpressions.PAYMENT_BATCH_CREATE}) {
            assertThat(passes(gate, COUNTRY_ADMIN)).as("country admin, %s", gate).isFalse();
            assertThat(passes(gate, MINISTRY_ADMIN)).as("ministry admin, %s", gate).isFalse();
            assertThat(passes(gate, ADMIN_ROLE)).as("Admin role, %s", gate).isFalse();
            assertThat(passes(gate, Set.of("ACCOUNTING.EDIT"))).as("ACCOUNTING.EDIT alone, %s", gate).isFalse();
            assertThat(passes(gate, FINANCE_OFFICER)).as("finance officer, %s", gate).isTrue();
            assertThat(passes(gate, SUPER_ADMIN)).as("super admin, %s", gate).isTrue();
        }
    }

    @Test
    @DisplayName("R5-02: the two create endpoints carry those gates")
    void endpointsCarryTheGates() {
        assertThat(gateOf(FundChainController.class, "create")).isEqualTo(MoheSecurityExpressions.ALLOCATION_CREATE);
        assertThat(gateOf(PaymentBatchController.class, "create")).isEqualTo(MoheSecurityExpressions.PAYMENT_BATCH_CREATE);
    }

    @Test
    @DisplayName("D-3: the chart, the FX rates and the budget are the ministry's; ACCOUNTING.EDIT alone no longer writes them")
    void ministryOwnsChartAndBudget() {
        String coa = MoheSecurityExpressions.COA_EDIT, budget = MoheSecurityExpressions.BUDGET_EDIT;
        assertThat(passes(coa, COUNTRY_ADMIN)).as("country admin, chart").isFalse();
        assertThat(passes(budget, COUNTRY_ADMIN)).as("country admin, budget").isFalse();
        assertThat(passes(coa, MINISTRY_ADMIN)).isTrue();
        assertThat(passes(budget, MINISTRY_ADMIN)).isTrue();
        assertThat(passes(coa, SUPER_ADMIN)).isTrue();
        assertThat(passes(budget, SUPER_ADMIN)).isTrue();
        assertThat(passes(budget, Set.of("BUDGET.EDIT"))).as("Finance Manager").isTrue();
        assertThat(passes(coa, ADMIN_ROLE)).as("the Admin role keeps the chart").isTrue();
        assertThat(passes(budget, ADMIN_ROLE)).as("and the budget, through BUDGET.EDIT (V146)").isTrue();
        assertThat(passes(budget, Set.of("ACCOUNTING.EDIT", "COA.EDIT"))).as("the chart codes alone do not edit the budget").isFalse();

        assertThat(gateOf(ChartOfAccountsController.class, "create")).isEqualTo(coa);
        assertThat(gateOf(ChartOfAccountsController.class, "update")).isEqualTo(coa);
        assertThat(gateOf(ChartOfAccountsController.class, "deactivate")).isEqualTo(coa);
        assertThat(gateOf(FxRateController.class, "create")).isEqualTo(coa);
        assertThat(gateOf(FxRateController.class, "delete")).isEqualTo(coa);
        assertThat(gateOf(BudgetAppropriationController.class, "create")).isEqualTo(budget);
        assertThat(gateOf(BudgetAppropriationController.class, "update")).isEqualTo(budget);
        assertThat(gateOf(BudgetAppropriationController.class, "approve")).isEqualTo(budget);
    }

    /** The {@code @PreAuthorize} of the one public method of that name. */
    static String gateOf(Class<?> controller, String method) {
        Method m = Arrays.stream(controller.getDeclaredMethods())
                .filter(x -> x.getName().equals(method)).findFirst().orElseThrow();
        PreAuthorize gate = AnnotatedElementUtils.findMergedAnnotation(m, PreAuthorize.class);
        assertThat(gate).as("%s.%s has a gate", controller.getSimpleName(), method).isNotNull();
        return gate.value();
    }

    /** Whether a caller holding {@code held} passes {@code expression}. */
    static boolean passes(String expression, Set<String> held) {
        StandardEvaluationContext context = new StandardEvaluationContext(new Caller(held));
        return Boolean.TRUE.equals(new SpelExpressionParser().parseExpression(expression).getValue(context, Boolean.class));
    }

    /** The one method of Spring Security's expression root these gates use. */
    public static final class Caller {
        private final Set<String> held;

        Caller(Set<String> held) {
            this.held = held;
        }

        public boolean hasAuthority(String code) {
            return held.contains(code);
        }
    }
}
