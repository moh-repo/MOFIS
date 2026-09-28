package com.dilaw.mohe.security.policy;

import com.dilaw.mohe.ums.model.AdminTier;

import java.util.List;
import java.util.Set;

/**
 * Everything a policy may know about the caller, resolved once per decision.
 *
 * <p>Two different "which countries" facts live here on purpose. {@link #tier}
 * is the account's standing (a ministry admin stays a ministry admin whatever
 * the country picker says), and {@link #isMinistryLevel()} reads it. The
 * {@link #countryIds} and {@link #allCountries} pair is the <em>request</em>
 * scope: a ministry officer who selected one attaché in the picker sees, and
 * acts on, that attaché only, exactly as {@code CountryScopeService.canAccessCountry}
 * has always answered. Policies that ask "may this account write Wafar" read the
 * tier; policies that ask "is this row in front of the caller" read the scope.
 *
 * @param userId       the signed-in account, or null for an anonymous caller
 * @param tier         the account's administrative tier (R-12)
 * @param allCountries true when the request scope is every country
 * @param countryIds   the request scope when {@code allCountries} is false
 * @param roleNames    direct and group roles
 * @param codes        effective permission codes (roles, groups, live direct grants)
 * @param positionCode the staff position (N-03), or null
 */
public record AccessSubject(Long userId, AdminTier tier, boolean allCountries, List<Integer> countryIds,
                            Set<String> roleNames, Set<String> codes, String positionCode) {

    public static AccessSubject anonymous() {
        return new AccessSubject(null, AdminTier.COUNTRY_STAFF, false, List.of(), Set.of(), Set.of(), null);
    }

    public boolean isAuthenticated() {
        return userId != null;
    }

    public boolean isSuperAdmin() {
        return tier == AdminTier.SUPER_ADMIN;
    }

    /** Super admin, ministry admin or ministry staff: acts for the ministry, not for one attaché. */
    public boolean isMinistryLevel() {
        return tier != null && tier.ministryLevel();
    }

    /** Whether the row's country is inside the caller's request scope. A null country is nobody's. */
    public boolean holdsCountry(Integer countryId) {
        if (countryId == null) return false;
        return allCountries || countryIds.contains(countryId);
    }

    public boolean hasCode(String code) {
        return code != null && codes.contains(code);
    }

    public boolean hasRole(String roleName) {
        return roleName != null && roleNames.contains(roleName);
    }

    public boolean is(Long otherUserId) {
        return userId != null && userId.equals(otherUserId);
    }

    public boolean is(Integer otherUserId) {
        return userId != null && otherUserId != null && userId.longValue() == otherUserId.longValue();
    }
}
