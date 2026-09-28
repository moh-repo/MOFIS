package com.dilaw.mohe.scope;

import com.dilaw.mohe.ums.service.CountryScopeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 0: who counts as ministry-wide.
 *
 * <p>{@code IsMinistryUser} is 0 on every row of {@code UMSCORE.Users}, so before
 * this change nobody was ministry-wide and scope came entirely from
 * {@code UserCountries}. {@code sa_superadmin} has no assignments at all, which
 * meant the account with the most authority in the system read nothing on every
 * scoped screen — and every screen scoped in this change would have joined them.
 *
 * <p>The rule is now {@code IsMinistryUser OR IsSuperAdmin}. Admin alone is
 * excluded on purpose: {@code jo_admin} and {@code sa_admin} administer one
 * attaché each.
 */
@Tag("integration")
@SpringBootTest
class SuperAdminIsMinistryWideTest {

    @Autowired private CountryScopeService countryScopeService;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("a super admin with no assigned countries is still ministry-wide")
    void superAdminWithoutAssignmentsSeesEverything() {
        Integer assignments = jdbc.queryForObject(
                "SELECT COUNT(*) FROM UMSCORE.UserCountries WHERE UserId = ? AND IsActive = 1",
                Integer.class, AsUser.SA_SUPERADMIN);
        assertThat(assignments)
                .as("the point of this account is that it has no assignments; give it some and the test proves nothing")
                .isZero();

        var scope = AsUser.call(AsUser.SA_SUPERADMIN, () -> countryScopeService.getScope());

        assertThat(scope.isMinistryUser())
                .as("""
                    Without this, an account with IsSuperAdmin = 1 and no UserCountries rows \
                    reads nothing anywhere — the strictest scope in the system belonged to \
                    its most privileged user.""")
                .isTrue();
        assertThat(scope.getAllowedCountryIds())
                .as("ministry scope is filled from the country lookup, not from assignments")
                .isNotEmpty();
    }

    @Test
    @DisplayName("an attaché's own admin stays scoped to that attaché")
    void countryAdminIsNotMinistryWide() {
        var jordan = AsUser.call(AsUser.JO_ADMIN, () -> countryScopeService.getScope());

        assertThat(jordan.isMinistryUser())
                .as("""
                    jo_admin administers Jordan. Treating IsAdmin as ministry-wide would have \
                    handed them Saudi Arabia's students as well.""")
                .isFalse();
        assertThat(jordan.isAdmin()).isTrue();
        assertThat(jordan.getAllowedCountryIds()).containsExactly(1004);
    }

    @Test
    @DisplayName("superadmin is ministry-wide despite being assigned only two countries")
    void superadminIsNotLimitedByItsAssignments() {
        var scope = AsUser.call(AsUser.SUPERADMIN, () -> countryScopeService.getScope());

        assertThat(scope.isMinistryUser()).isTrue();
        assertThat(scope.getAllowedCountryIds())
                .as("assigned Egypt and Unspecified, but ministry scope covers every country")
                .hasSizeGreaterThan(2);
    }

    @Test
    @DisplayName("an ordinary officer is exactly their assigned countries")
    void officerScopeIsTheirAssignments() {
        var saudi = AsUser.call(AsUser.SA_ADMIN, () -> countryScopeService.getScope());

        assertThat(saudi.isMinistryUser()).isFalse();
        assertThat(saudi.getAllowedCountryIds()).containsExactly(1005);
    }
}
