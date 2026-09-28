package com.dilaw.mohe.scope;

import com.dilaw.mohe.accounting.model.AttacheAdvanceBalance;
import com.dilaw.mohe.accounting.model.CoaAccount;
import com.dilaw.mohe.accounting.model.FiscalYear;
import com.dilaw.mohe.accounting.service.AdvanceBalanceService;
import com.dilaw.mohe.accounting.service.AllocationService;
import com.dilaw.mohe.accounting.service.CoaAccountService;
import com.dilaw.mohe.accounting.service.FiscalPeriodService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The two accounting pages: the chart of accounts and attaché advances.
 *
 * <p>They need <em>opposite</em> treatment of a null country, and getting that
 * backwards either leaks or breaks:
 *
 * <ul>
 *   <li>{@code COA_ACCOUNT} — a null is the الدليل المحاسبي الموحد, the unified
 *       chart every attaché books against. Hidden, a country officer has an empty
 *       chart and nothing to classify against.
 *   <li>{@code ATTACHE_ADVANCE_BALANCE} — the column is NOT NULL, because an
 *       attaché <em>is</em> a country. There is no shared row to allow for.
 *   <li>{@code FISCAL_YEAR} — all three rows are null and shared, because 2024 is
 *       2024 everywhere. Fail closed and the year selector empties, taking both
 *       pages with it.
 * </ul>
 *
 * <p>What makes the permissive reading safe is the write side, and that is what
 * most of these tests are really checking: only a ministry user can produce a
 * null-country account.
 *
 * <p>Writes are undone in {@link #cleanUp()}.
 */
@Tag("integration")
@SpringBootTest
class AccountingPagesScopeTest {

    @Autowired private CoaAccountService coaAccountService;
    @Autowired private AdvanceBalanceService advanceBalanceService;
    @Autowired private FiscalPeriodService fiscalPeriodService;
    @Autowired private AllocationService allocationService;
    @Autowired private JdbcTemplate jdbc;

    private static final String TEST_CODE_PREFIX = "ZZTEST";

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM FINCORE.COA_ACCOUNT WHERE CODE LIKE ?", TEST_CODE_PREFIX + "%");
    }

    private Integer fiscalYear2026() {
        return jdbc.queryForObject(
                "SELECT FISCAL_YEAR_ID FROM FINCORE.FISCAL_YEAR WHERE YEAR_NO = 2026", Integer.class);
    }

    // ── /accounting/chart-of-accounts ────────────────────────────────────────

    /**
     * The segment these tests build in. Since V064 the economic segment holds the
     * ministry's revenue chart with the manual's code widths (1/1/1/2), which a
     * {@code ZZTEST…} code cannot satisfy — so the scratch accounts go into the
     * PROJECT segment, which the Regulation has not yet filled and whose widths
     * are unset.
     */
    private Integer testSegment() {
        return jdbc.queryForObject(
                "SELECT SEGMENT_ID FROM FINCORE.COA_SEGMENT WHERE SEGMENT_CODE = 'PROJECT'", Integer.class);
    }

    @Test
    @DisplayName("the test segment ships empty, so these tests build what they measure")
    void testSegmentIsStillEmpty() {
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM FINCORE.COA_ACCOUNT WHERE SEGMENT_ID = ? AND CODE NOT LIKE ?",
                        Integer.class, testSegment(), TEST_CODE_PREFIX + "%"))
                .as("""
                    Account codes come from the official manual and are imported, never \
                    invented — the economic segment's come from V064. If this ever fails, \
                    someone has committed codes into the project segment.""")
                .isZero();
    }

    @Test
    @DisplayName("the unified chart is visible to every attaché; one attaché's additions are not")
    void sharedAccountsAreSharedAndOwnedOnesAreNot() {
        Integer year = fiscalYear2026();
        assumeTrue(year != null, "no 2026 fiscal year");

        // A ministry account with no country: the unified chart.
        CoaAccount shared = AsUser.call(AsUser.SUPERADMIN, () ->
                coaAccountService.create(draft(year, TEST_CODE_PREFIX + "1", null)));
        // And one belonging to Egypt alone.
        CoaAccount egyptOnly = AsUser.call(AsUser.SUPERADMIN, () ->
                coaAccountService.create(draft(year, TEST_CODE_PREFIX + "2", 1)));

        List<CoaAccount> jordanSees = AsUser.call(AsUser.JO_ADMIN, () -> coaAccountService.forYear(year));

        assertThat(jordanSees).extracting(CoaAccount::getId)
                .as("""
                    The unified chart has to reach every attaché. Scoping it the way an advance \
                    balance is scoped would leave Jordan with nothing to book against.""")
                .contains(shared.getId())
                .doesNotContain(egyptOnly.getId());

        assertThat(AsUser.call(AsUser.ADMIN, () -> coaAccountService.forYear(year)))
                .extracting(CoaAccount::getId)
                .as("positive control: an officer holding every country sees both")
                .contains(shared.getId(), egyptOnly.getId());
    }

    @Test
    @DisplayName("only the ministry may add to the unified chart")
    void anOfficerCannotCreateASharedAccount() {
        Integer year = fiscalYear2026();
        assumeTrue(year != null, "no 2026 fiscal year");

        // jo_admin holds exactly one country, so an unspecified country is taken as
        // theirs rather than refused — and crucially not as null.
        CoaAccount created = AsUser.call(AsUser.JO_ADMIN, () ->
                coaAccountService.create(draft(year, TEST_CODE_PREFIX + "3", null)));

        assertThat(created.getStudyCountryInId())
                .as("""
                    This is what makes the permissive read safe. If an officer could store a null \
                    country, they could put an account into every other attaché's chart.""")
                .isEqualTo(1004);

        assertThatThrownBy(() -> AsUser.call(AsUser.JO_ADMIN, () ->
                coaAccountService.create(draft(year, TEST_CODE_PREFIX + "4", 1))))
                .as("nor may they file one under somebody else's attaché")
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("another attaché's account cannot be opened, renamed or retired")
    void accountByIdIsScoped() {
        Integer year = fiscalYear2026();
        assumeTrue(year != null, "no 2026 fiscal year");

        CoaAccount egyptOnly = AsUser.call(AsUser.SUPERADMIN, () ->
                coaAccountService.create(draft(year, TEST_CODE_PREFIX + "5", 1)));

        assertThat(AsUser.call(AsUser.SUPERADMIN, () -> coaAccountService.require(egyptOnly.getId())))
                .as("it exists, so the refusals below are about scope")
                .isNotNull();

        // 404 rather than 403: account ids are sequential, and a 403 confirms which
        // ones another attaché has.
        assertThatThrownBy(() -> AsUser.call(AsUser.JO_ADMIN, () -> coaAccountService.require(egyptOnly.getId())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");

        assertThatThrownBy(() -> AsUser.call(AsUser.JO_ADMIN, () -> coaAccountService.deactivate(egyptOnly.getId())))
                .as("deactivate goes through the same gate, so it cannot reach further than the read")
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    @DisplayName("the summary counts what the caller can see, not what exists")
    void summaryCountsAreScoped() {
        Integer year = fiscalYear2026();
        assumeTrue(year != null, "no 2026 fiscal year");

        AsUser.call(AsUser.SUPERADMIN, () -> coaAccountService.create(draft(year, TEST_CODE_PREFIX + "6", 1)));

        assertThat(AsUser.call(AsUser.JO_ADMIN, () -> coaAccountService.forSegment(testSegment(), year)))
                .as("the account count on the page header is a fact about somebody's chart")
                .isEmpty();
    }

    // ── /accounting/advance-balances ─────────────────────────────────────────

    @Test
    @DisplayName("an officer reads only their own attaché's advance")
    void advancesAreScoped() {
        Integer year = jdbc.queryForObject(
                "SELECT TOP 1 FISCAL_YEAR_ID FROM FINCORE.ATTACHE_ADVANCE_BALANCE", Integer.class);
        assumeTrue(year != null, "no advance balances");

        List<AttacheAdvanceBalance> ministry =
                AsUser.call(AsUser.SUPERADMIN, () -> advanceBalanceService.forYear(year));
        assertThat(ministry).isNotEmpty();

        assertThat(AsUser.call(AsUser.JO_ADMIN, () -> advanceBalanceService.forYear(year)))
                .as("""
                    An advance balance is how much cash one office holds and how much is already \
                    committed — the most directly country-owned figure in the module.""")
                .isEmpty();

        assertThat(AsUser.call(AsUser.ADMIN, () -> advanceBalanceService.forYear(year)))
                .as("positive control: every country assigned, so every balance")
                .hasSameSizeAs(ministry);
    }

    @Test
    @DisplayName("the reconciliation covers the caller's own attachés")
    void reconciliationIsScoped() {
        Integer year = jdbc.queryForObject(
                "SELECT TOP 1 FISCAL_YEAR_ID FROM FINCORE.ATTACHE_ADVANCE_BALANCE", Integer.class);
        assumeTrue(year != null, "no advance balances");

        // Being told the ministry reconciles is no use to an officer; being told
        // their own attaché does not is the entire point of the check.
        assertThat(AsUser.call(AsUser.JO_ADMIN, () -> advanceBalanceService.reconcile(year)))
                .as("nothing in scope, so nothing to reconcile — not a verdict on somebody else's books")
                .isEmpty();
    }

    @Test
    @DisplayName("another attaché's opening balance cannot be set")
    void openingBalanceIsScoped() {
        Integer year = fiscalYear2026();
        assumeTrue(year != null, "no 2026 fiscal year");

        assertThatThrownBy(() -> AsUser.call(AsUser.JO_ADMIN,
                () -> advanceBalanceService.setOpeningBalance(year, 1, 1, new BigDecimal("100"))))
                .as("""
                    Opening cash is the starting point of an attaché's whole year. Setting \
                    somebody else's would silently move every figure that follows.""")
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("an allocation cannot be created for, or approved at, another attaché")
    void allocationsAreScoped() {
        Integer quarter = jdbc.queryForObject(
                "SELECT TOP 1 MOHE_QUARTER_KEY FROM FINCORE.ATTACHE_ALLOCATION", Integer.class);
        assumeTrue(quarter != null, "no allocations");

        assertThat(AsUser.call(AsUser.SUPERADMIN, () -> allocationService.forQuarter(quarter))).isNotEmpty();
        assertThat(AsUser.call(AsUser.JO_ADMIN, () -> allocationService.forQuarter(quarter)))
                .as("how much another attaché received is that attaché's business")
                .isEmpty();

        Integer allocationId = jdbc.queryForObject(
                "SELECT TOP 1 ALLOCATION_ID FROM FINCORE.ATTACHE_ALLOCATION WHERE STUDY_COUNTRY_IN_ID = 1",
                Integer.class);
        assumeTrue(allocationId != null, "no Egyptian allocations");

        assertThatThrownBy(() -> AsUser.call(AsUser.JO_ADMIN, () -> allocationService.require(allocationId)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    // ── the calendar both pages hang off ─────────────────────────────────────

    @Test
    @DisplayName("the shared fiscal calendar stays visible, or both pages go blank")
    void fiscalYearsAreShared() {
        List<FiscalYear> ministry = AsUser.call(AsUser.SUPERADMIN, () -> fiscalPeriodService.years());
        assumeTrue(!ministry.isEmpty(), "no fiscal years");

        assertThat(AsUser.call(AsUser.JO_ADMIN, () -> fiscalPeriodService.years()))
                .as("""
                    All three rows have no country because 2024 is 2024 everywhere. Treating that \
                    null as ministry-only would empty the year selector that the chart of accounts \
                    and advance balances screens both depend on — the fix would break the pages it \
                    was meant to protect.""")
                .hasSameSizeAs(ministry);

        assertThat(AsUser.call(AsUser.JO_ADMIN, () -> fiscalPeriodService.periodsOf(ministry.get(0).getId())))
                .as("and the periods inside a visible year come with it")
                .isNotEmpty();
    }

    private CoaAccount draft(Integer fiscalYearId, String code, Integer countryId) {
        CoaAccount a = new CoaAccount();
        a.setSegmentId(testSegment());
        a.setFiscalYearId(fiscalYearId);
        a.setCode(code);
        a.setNameAr("حساب اختبار");
        a.setNameEn("Test account");
        a.setAccountNature("EXPENSE");
        a.setPostable(true);
        a.setStudyCountryInId(countryId);
        return a;
    }
}
