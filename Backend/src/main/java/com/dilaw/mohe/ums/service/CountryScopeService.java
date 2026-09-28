package com.dilaw.mohe.ums.service;

import com.dilaw.mohe.service.CountryNameService;
import com.dilaw.mohe.ums.model.UmsUser;
import com.dilaw.mohe.ums.model.UmsUserCountry;
import com.dilaw.mohe.ums.repository.UmsUserCountryRepository;
import com.dilaw.mohe.ums.repository.UmsUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.dilaw.mohe.config.SecurityRequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Resolves the current user's country scope for multi-country / ministry access.
 * Ministry users have scope over all countries; country users are restricted to their assigned UserCountries.
 */
@Service
public class CountryScopeService {

    /** Optional header from the frontend country selector; narrows read scope to one study country. */
    public static final String HEADER_SELECTED_COUNTRY = "X-Country-Id";

    @Autowired
    private UmsUserRepository userRepository;

    /**
     * Read directly rather than through {@code UmsUserService}.
     *
     * <p>This used to call {@code userService.getUserCountryIds}, which is itself
     * one line over this repository. Keeping the dependency would make the graph
     * circular the moment {@code UmsUserService} needs a scope of its own — which
     * it now does, to scope {@code /ums-admin/users} — and a cycle broken with
     * {@code @Lazy} is a cycle that still exists.
     */
    @Autowired
    private UmsUserCountryRepository userCountryRepository;

    @Autowired
    private CountryNameService countryNameService;

    /** R-12: the tier from the roles (flags as fallback), the one answer every scope question starts from. */
    @Autowired
    private AdminTierService adminTierService;

    /**
     * Current user id from JWT (Spring Security). Returns empty if not authenticated.
     */
    public Optional<Long> getCurrentUserId() {
        return SecurityRequestUtils.currentUserId();
    }

    /**
     * Get the country scope for the current request user.
     * If no user context, returns a scope with no allowed countries (so enforcement will deny).
     */
    /**
     * R-17: the policy evaluator behind {@link #validateCountryAccess} and
     * {@link #canAccessCountry}. Lazy, because the evaluator's subject resolver
     * reads this service's scope.
     */
    @Autowired
    @org.springframework.context.annotation.Lazy
    private com.dilaw.mohe.security.policy.AccessControl accessControl;

    public CountryScope getScope() {
        return getCurrentUserId()
                .map(userId -> narrowBySelectedCountry(getScopeForUser(userId)))
                .orElse(CountryScope.noAccess());
    }

    /**
     * When the client sends {@link #HEADER_SELECTED_COUNTRY}, narrow the base scope to that single
     * study country if it is within the user's allowed set. Ministry scope is narrowed with
     * {@code ministryUser=false} so JPA country specs apply the filter.
     */
    private CountryScope narrowBySelectedCountry(CountryScope base) {
        Integer selectedId = readSelectedCountryHeader();
        if (selectedId == null) {
            return base;
        }
        List<Integer> allowed = base.getAllowedCountryIds();
        if (allowed.isEmpty() || !allowed.contains(selectedId)) {
            return base;
        }
        return new CountryScope(base.getUserId(), false, base.isAdmin(), List.of(selectedId), base.getTier());
    }

    private Integer readSelectedCountryHeader() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return null;
            }
            HttpServletRequest request = attrs.getRequest();
            if (request == null) {
                return null;
            }
            String header = request.getHeader(HEADER_SELECTED_COUNTRY);
            if (header == null || header.isBlank()) {
                return null;
            }
            int id = Integer.parseInt(header.trim());
            return id > 0 ? id : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Get the country scope for a specific user (e.g. for auth response).
     */
    public CountryScope getScopeForUser(Long userId) {
        return getScopeForUser(userRepository.findById(userId).orElse(null));
    }

    private CountryScope getScopeForUser(UmsUser user) {
        if (user == null) return CountryScope.noAccess();
        // R-12 (6 Sep 2026): the tier decides. A super admin, a ministry admin and
        // ministry staff are ministry-wide whatever UserCountries says; a country
        // admin and country staff have exactly their assignments.
        //
        // Before this the flag was the only thing granting ministry scope, and it
        // was 0 on every row of UMSCORE.Users, so nobody was ministry-wide and
        // scope came entirely from assignments: sa_superadmin has none, which
        // made every scoped page return nothing for the account with the most
        // authority. And admin was ministry-wide only because it happened to
        // hold all sixteen attaches.
        //
        // Deliberately not IsAdmin alone: jo_admin and sa_admin are their own
        // attache's administrators, and treating them as ministry-wide would let
        // Jordan read Saudi students.
        com.dilaw.mohe.ums.model.AdminTier tier =
                adminTierService.tierOf(user, adminTierService.roleNamesOf(user.getId()));
        boolean isMinistry = tier.ministryLevel();
        boolean isAdmin = tier.administersStaff();
        List<Integer> countryIds;
        if (isMinistry) {
            countryIds = countryNameService.allIds();
        } else {
            countryIds = userCountryRepository.findByUserIdAndIsActiveTrue(user.getId()).stream()
                    .map(UmsUserCountry::getCountryId)
                    .collect(Collectors.toList());
        }
        return new CountryScope(user.getId(), isMinistry, isAdmin, countryIds, tier);
    }

    /**
     * Validate that the current user is allowed to access the given country.
     * @param countryId country to check (may be null for "no filter" - then we only check that user has some scope)
     * @throws SecurityException if user has no scope or (for non-ministry) countryId is not in allowed list
     */
    public void validateCountryAccess(Integer countryId) {
        CountryScope scope = getScope();
        if (scope.getAllowedCountryIds().isEmpty() && !scope.isMinistryUser()) {
            throw new SecurityException("Access denied: no country scope");
        }
        // R-17 (phase 4): the decision is the evaluator's - CountryScopedPolicy,
        // the same rule the resource policies call first - so every remaining
        // coarse site refuses through PolicyDenied and is audited ACCESS_DENIED.
        if (countryId != null) {
            accessControl.assertAllowed(new com.dilaw.mohe.security.policy.CountryOwned("Record", countryId), "ACCESS");
        }
    }

    /**
     * Resolve the effective country filter for the current user.
     * If requestCountryId is provided and allowed, return it; if ministry and null, return null (all);
     * if country user and null, return first allowed country or null (caller may restrict to allowed list).
     */
    public Integer resolveCountryFilter(Integer requestCountryId) {
        return getEffectiveCountryIdForRead(requestCountryId);
    }

    /**
     * Effective country ID for read/list APIs: ministry gets request as-is; country user gets allowed id or first allowed when null.
     */
    public Integer getEffectiveCountryIdForRead(Integer requestCountryId) {
        CountryScope scope = getScope();
        if (scope.isMinistryUser()) return requestCountryId;
        List<Integer> allowed = scope.getAllowedCountryIds();
        if (allowed.isEmpty()) return null;
        if (requestCountryId == null) return allowed.get(0);
        return allowed.contains(requestCountryId) ? requestCountryId : null;
    }

    /**
     * Validate that the current user can use this countryId for a read request. Throws SecurityException if not in scope.
     */
    public void validateCountryForRead(Integer requestCountryId) {
        if (requestCountryId == null) return;
        validateCountryAccess(requestCountryId);
    }

    /**
     * Whether the current user can access all countries (ministry). If false, they are restricted to allowedCountryIds.
     */
    public boolean canAccessAllCountries() {
        return getScope().isMinistryUser();
    }

    /**
     * Whether the signed-in user acts for the ministry rather than for one attaché.
     *
     * <p>True for a super admin, a ministry user, and an admin whose assignments
     * cover every study country — the {@code admin} account, which is a ministry
     * admin in practice even though its flag is the same one {@code jo_admin}
     * carries. Read from the <em>base</em> scope, not the request-narrowed one:
     * choosing a country in the picker narrows what a ministry officer sees, it
     * does not turn them into that country's staff.
     *
     * <p>Security plan N-04 (6 Sep 2026); since R-12 the answer is the tier
     * role ({@link AdminTierService}), with the flags as the fallback.
     */
    public boolean isMinistryLevelActor() {
        Long userId = getCurrentUserId().orElse(null);
        return userId != null && isMinistryLevelUser(userId);
    }

    /** The same test for a given account: the login response tells the frontend what to hide. */
    public boolean isMinistryLevelUser(Long userId) {
        return userId != null && adminTierService.tierOf(userId).ministryLevel();
    }

    /**
     * The gate for data one attaché owns outright — Wafar today (N-04), and any
     * table the same rule is later extended to.
     *
     * <p>Refuses with 403 when the caller is {@link #isMinistryLevelActor()
     * ministry-level}, whatever codes they hold, and otherwise requires the
     * country to be in the caller's scope. A null country is refused too: on
     * Wafar a missing country is missing data, and nobody owns it until the
     * ministry attributes it through a correction request.
     *
     * @param countryId the country the row belongs to
     * @param what      short noun for the message, e.g. {@code "Wafar spending"}
     */
    public void assertCountryOwnedWrite(Integer countryId, String what) {
        if (isMinistryLevelActor()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    what + " is recorded and approved by the attaché that holds it; ministry accounts may only view it.");
        }
        if (countryId == null || !canAccessCountry(countryId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    what + " belongs to an attaché outside your scope.");
        }
    }

    /**
     * Check whether the current user is allowed to access the given country (for entity country checks).
     *
     * <p><strong>A null country returns true</strong> — read it as "there is no
     * country to check", not "everyone may see this". That is right where the
     * column is optional and wrong wherever a missing country means missing data:
     * on those records the caller must reject a null itself before asking. See
     * {@code StudentDataService#getStudentData} and
     * {@code StudyFinReceivedWhomService#mayAccess}, both of which do.
     */
    public boolean canAccessCountry(Integer countryId) {
        if (countryId == null) return true;
        // R-17 (phase 4): same rule, same place - CountryScopedPolicy through the evaluator.
        return accessControl.isAllowed(new com.dilaw.mohe.security.policy.CountryOwned("Record", countryId), "ACCESS");
    }

    /**
     * Immutable scope for the current user: ministry flag and allowed country IDs.
     */
    public static class CountryScope {
        private final Long userId;
        private final boolean ministryUser;
        private final boolean admin;
        private final List<Integer> allowedCountryIds;
        private final com.dilaw.mohe.ums.model.AdminTier tier;

        public CountryScope(Long userId, boolean ministryUser, List<Integer> allowedCountryIds) {
            this(userId, ministryUser, false, allowedCountryIds);
        }

        public CountryScope(Long userId, boolean ministryUser, boolean admin, List<Integer> allowedCountryIds) {
            this(userId, ministryUser, admin, allowedCountryIds, null);
        }

        public CountryScope(Long userId, boolean ministryUser, boolean admin, List<Integer> allowedCountryIds,
                            com.dilaw.mohe.ums.model.AdminTier tier) {
            this.userId = userId;
            this.ministryUser = ministryUser;
            this.admin = admin;
            this.allowedCountryIds = allowedCountryIds != null ? List.copyOf(allowedCountryIds) : List.of();
            this.tier = tier != null ? tier : inferTier(ministryUser, admin);
        }

        private static com.dilaw.mohe.ums.model.AdminTier inferTier(boolean ministryUser, boolean admin) {
            if (admin && ministryUser) return com.dilaw.mohe.ums.model.AdminTier.MINISTRY_ADMIN;
            if (admin) return com.dilaw.mohe.ums.model.AdminTier.COUNTRY_ADMIN;
            if (ministryUser) return com.dilaw.mohe.ums.model.AdminTier.MINISTRY_STAFF;
            return com.dilaw.mohe.ums.model.AdminTier.COUNTRY_STAFF;
        }

        public static CountryScope noAccess() {
            return new CountryScope(null, false, false, Collections.emptyList());
        }

        public Long getUserId() { return userId; }
        public boolean isMinistryUser() { return ministryUser; }
        public boolean isAdmin() { return admin; }
        public List<Integer> getAllowedCountryIds() { return allowedCountryIds; }
        /** R-12: the tier the scope was built from (a country selection narrows the countries, not the tier). */
        public com.dilaw.mohe.ums.model.AdminTier getTier() { return tier; }

        /** Role level string written to CREATED_ROLE_LEVEL on new student records. */
        public String roleLevel() {
            return switch (tier) {
                case SUPER_ADMIN -> "SUPERADMIN";
                case MINISTRY_ADMIN, COUNTRY_ADMIN -> "ADMIN";
                case MINISTRY_STAFF -> "MINISTRY";
                case COUNTRY_STAFF -> "STAFF";
            };
        }
    }
}
