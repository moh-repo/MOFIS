package com.dilaw.mohe.security.policy;

import com.dilaw.mohe.config.SecurityRequestUtils;
import com.dilaw.mohe.ums.repository.UmsUserRepository;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.UserAuthorityCache;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Builds the {@link AccessSubject} for the signed-in caller, or for any account.
 *
 * <p>Reads nothing the request has not already produced: the tier, roles and
 * codes come from {@link UserAuthorityCache} (the same snapshot the JWT filter
 * granted, so a decision can never see a right the filter did not), and the
 * request scope from {@link CountryScopeService#getScope()}, country picker
 * included.
 */
@Service
public class AccessSubjectResolver {

    private final CountryScopeService countryScopeService;
    private final UserAuthorityCache authorityCache;
    private final UmsUserRepository userRepository;

    public AccessSubjectResolver(CountryScopeService countryScopeService, UserAuthorityCache authorityCache,
                                 UmsUserRepository userRepository) {
        this.countryScopeService = countryScopeService;
        this.authorityCache = authorityCache;
        this.userRepository = userRepository;
    }

    /** The signed-in caller; anonymous when there is none. */
    public AccessSubject current() {
        Long userId = SecurityRequestUtils.currentUserId().orElse(null);
        if (userId == null) return AccessSubject.anonymous();
        return build(userId, countryScopeService.getScope());
    }

    /** Any account, on its base scope (no country picker): for eligibility lists and reports. */
    public AccessSubject of(Long userId) {
        if (userId == null) return AccessSubject.anonymous();
        CountryScopeService.CountryScope scope = countryScopeService.getScopeForUser(userId);
        if (scope == null) return AccessSubject.anonymous();
        return build(userId, scope);
    }

    private AccessSubject build(Long userId, CountryScopeService.CountryScope scope) {
        UserAuthorityCache.Snapshot snapshot = authorityCache.snapshot(userId);
        if (snapshot == null || snapshot.deleted()) return AccessSubject.anonymous();
        // The position is read live, not from the cache: it is set on the staff row
        // (by the users screen, an import, or a DBA) and a stale value would route an
        // approval to the wrong desk for a minute. One indexed read per decision.
        String position = userRepository.findById(userId).map(u -> u.getPositionCode()).orElse(snapshot.positionCode());
        return new AccessSubject(userId, snapshot.tier(), scope.isMinistryUser(), scope.getAllowedCountryIds(),
                snapshot.roleNames(), Set.copyOf(snapshot.codes()), position);
    }
}
