package com.dilaw.mohe.scope;

import com.dilaw.mohe.wafar.dto.WafarBalanceSummaryDto;
import com.dilaw.mohe.wafar.dto.WafarSpendingDto;
import com.dilaw.mohe.wafar.service.WafarBalanceService;
import com.dilaw.mohe.wafar.service.WafarSpendingService;
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
 * The Wafar page, which had no country scoping of any kind.
 *
 * <p>Wafar is budget already transferred to one attaché and not disbursed, so it
 * stays there: Cairo's savings pay Cairo's students and nobody else's. The balance
 * card returned one ministry-wide figure and the transaction list returned every
 * country's rows, both of which contradict that rule outright.
 *
 * <p>Read-only.
 */
@Tag("integration")
@SpringBootTest
class WafarCountryScopeTest {

    @Autowired private WafarSpendingService spendingService;
    @Autowired private WafarBalanceService balanceService;
    @Autowired private JdbcTemplate jdbc;

    private List<WafarSpendingDto> listAs(long userId) {
        return AsUser.call(userId, () -> spendingService.getAll(null, null, null, null, null));
    }

    @Test
    @DisplayName("an officer sees only their own attaché's transactions")
    void transactionsAreScoped() {
        List<WafarSpendingDto> ministry = listAs(AsUser.SUPERADMIN);
        assumeTrue(!ministry.isEmpty(), "no Wafar transactions");

        assertThat(listAs(AsUser.JO_ADMIN))
                .as("every Wafar row in this database is Egyptian or unattributed; Jordan has none")
                .isEmpty();

        assertThat(listAs(3L))
                .as("""
                    Positive control. hr_manager is country staff holding Egypt, so it takes the \
                    scoped path — it must still see the Egyptian rows. Without this, "Jordan sees \
                    nothing" would also pass if the filter returned nothing to everybody.""")
                .isNotEmpty();
    }

    @Test
    @DisplayName("transactions with no attaché are ministry-only")
    void unattributedTransactionsAreMinistryOnly() {
        Integer unattributed = jdbc.queryForObject(
                "SELECT COUNT(*) FROM MOHECORE.WAFAR_SPENDING WHERE STUDY_COUNTRY_IN_ID IS NULL", Integer.class);
        assumeTrue(unattributed != null && unattributed > 0, "everything is attributed");

        // These eleven are attaché running costs — rent, salaries, transport — with
        // no student and no ticket, so V046 could not derive a country and did not
        // invent one. A country-scoped account must not see them, however many
        // attachés it holds: "belongs to no attaché" is not the same as "belongs
        // to all of them". hr_manager holds Egypt, where every attributed row is.
        // (`admin` no longer serves here: since R-12 it is the MINISTRY_ADMIN tier
        // and ministry-scoped, so it sees the unattributed rows like the super admin.)
        long egyptOfficer = 3L;
        List<WafarSpendingDto> scoped = listAs(egyptOfficer);
        assertThat(scoped)
                .as("a row with no country matches no IN list, however complete")
                .allSatisfy(row -> assertThat(row.getStudyCountryInId()).isNotNull());

        assertThat(listAs(AsUser.SUPERADMIN))
                .as("the ministry sees them, and is the only one that can attribute them")
                .hasSizeGreaterThan(scoped.size());
        assertThat(listAs(AsUser.ADMIN))
                .as("the ministry admin is ministry-scoped since R-12 and sees them too")
                .hasSameSizeAs(listAs(AsUser.SUPERADMIN));
    }

    @Test
    @DisplayName("the balance is the caller's attaché, not the ministry's")
    void balanceIsScoped() {
        WafarBalanceSummaryDto ministry = AsUser.call(AsUser.SUPERADMIN, () -> balanceService.getSummary());
        WafarBalanceSummaryDto jordan = AsUser.call(AsUser.JO_ADMIN, () -> balanceService.getSummary());

        assumeTrue(ministry.getTotalDeposits().compareTo(BigDecimal.ZERO) > 0, "no deposits");

        assertThat(jordan.getTotalDeposits())
                .as("""
                    One global figure implied Cairo's savings could pay Riyadh's students, which \
                    is the single thing Wafar cannot do.""")
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(jordan.getCurrentBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("both halves of the balance are scoped the same way")
    void depositsAndSpendingAreScopedTogether() {
        WafarBalanceSummaryDto adminView = AsUser.call(AsUser.ADMIN, () -> balanceService.getSummary());

        // If deposits were filtered and spending was not — or the reverse — the
        // subtraction would read as a surplus or a deficit that does not exist.
        assertThat(adminView.getCurrentBalance())
                .as("balance must equal deposits minus approved spending, under the same filter")
                .isEqualByComparingTo(adminView.getTotalDeposits().subtract(adminView.getTotalSpent()));
    }

    @Test
    @DisplayName("a transaction cannot be opened, approved or cancelled from another attaché")
    void mutationsByIdAreScoped() {
        Integer egyptian = jdbc.queryForObject(
                "SELECT TOP 1 SPENDING_ID FROM MOHECORE.WAFAR_SPENDING WHERE STUDY_COUNTRY_IN_ID = 1", Integer.class);
        assumeTrue(egyptian != null, "no Egyptian Wafar transactions");

        assertThat(AsUser.call(AsUser.SUPERADMIN, () -> spendingService.getById(egyptian)))
                .as("it exists, so the refusals below are about scope and not about the id")
                .isNotNull();

        // 404 rather than 403 on purpose: a 403 confirms the record is there, which
        // is all that is needed to walk another attaché's transaction ids.
        assertThatThrownBy(() -> AsUser.call(AsUser.JO_ADMIN, () -> spendingService.getById(egyptian)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");

        assertThatThrownBy(() -> AsUser.run(AsUser.JO_ADMIN, () -> spendingService.cancel(egyptian)))
                .as("every mutation goes through the same gate as the read")
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    @DisplayName("spending is stamped with an attaché rather than left unattributable")
    void createRefusesWithoutAnAttache() {
        var dto = new com.dilaw.mohe.wafar.dto.WafarSpendingCreateDto();
        dto.setCategoryId(jdbc.queryForObject(
                "SELECT TOP 1 CATEGORY_ID FROM MOHECORE.WAFAR_SPENDING_CATEGORY", Integer.class));
        dto.setAmount(new BigDecimal("1.00"));
        dto.setSpendingDate(java.time.LocalDate.now());
        dto.setBeneficiaryType("OTHER");

        // An officer holding two attachés has no single one to default to, and
        // guessing is how the eleven unattributable rows already in this table
        // came to exist. hr_manager holds Egypt + Unspecified and is not an admin.
        long twoCountries = 3L;
        assertThatThrownBy(() -> AsUser.call(twoCountries, () -> spendingService.create(dto)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");

        // A ministry account is refused earlier and differently: since N-04 (6 Sep
        // 2026) it may not record a spend at all, so it gets the ownership 403
        // rather than "name the attaché".
        assertThatThrownBy(() -> AsUser.call(AsUser.SUPERADMIN, () -> spendingService.create(dto)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }
}
