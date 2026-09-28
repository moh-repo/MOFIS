package com.dilaw.mohe.ums.service;

import com.dilaw.mohe.config.CacheConfig;
import com.dilaw.mohe.ums.model.AdminTier;
import com.dilaw.mohe.ums.model.UmsUser;
import com.dilaw.mohe.ums.repository.UmsUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * What the JWT filter needs per request, cached for a short while.
 *
 * <p>Security plan R-19. {@code JwtAuthFilter} used to run two queries per
 * request (the permission union and the account row) on top of the session
 * check. The union is the expensive one: roles, groups, group roles, direct
 * grants. It is now computed once per user per
 * {@code ums.authority-cache.ttl-seconds} and evicted explicitly whenever
 * something that feeds it changes: a role, group, permission or country
 * assignment on the user ({@code UmsUserService}); a role's permission set or a
 * group's roles ({@code UmsRoleService}, {@code UmsGroupService}, every entry,
 * because many users may be affected); a flag change; a delete; a logout or a
 * session termination. The "changes apply immediately" property the system
 * had is kept; the TTL is only a backstop.
 *
 * <p>The session check stays a database read on every request: it is what
 * makes revocation immediate, and caching it would undo that.
 *
 * <p><b>Single instance.</b> Eviction is in-process (decision D-10). A second
 * application instance would serve stale authorities for up to the TTL after a
 * change made on the first; set the TTL to 0 there, or add a shared eviction
 * signal, before deploying more than one.
 */
@Service
public class UserAuthorityCache {

    /** Everything the filter derives from the account, in one cacheable value. */
    public record Snapshot(Long userId, boolean deleted, List<String> codes, Set<String> roleNames, AdminTier tier,
                           String positionCode) {

        /** The authorities the JWT filter grants: the codes plus {@code ROLE_<tier>}. */
        public List<String> authorities() {
            List<String> out = new ArrayList<>(codes);
            if (tier != AdminTier.COUNTRY_STAFF) out.add(tier.authority());
            return out;
        }
    }

    @Autowired private UmsPermissionService permissionService;
    @Autowired private AdminTierService adminTierService;
    @Autowired private UmsUserRepository userRepository;

    @Cacheable(cacheNames = CacheConfig.USER_AUTHORITIES, key = "#userId")
    @Transactional(readOnly = true)
    public Snapshot snapshot(Long userId) {
        UmsUser user = userRepository.findById(userId).orElse(null);
        if (user == null || Boolean.TRUE.equals(user.getIsDeleted())) {
            return new Snapshot(userId, true, List.of(), Set.of(), AdminTier.COUNTRY_STAFF, null);
        }
        Set<String> roleNames = adminTierService.roleNamesOf(userId);
        AdminTier tier = adminTierService.tierOf(user, roleNames);
        List<String> codes = List.copyOf(permissionService.getUserPermissionCodes(userId));
        return new Snapshot(userId, false, codes, roleNames, tier, user.getPositionCode());
    }

    @CacheEvict(cacheNames = CacheConfig.USER_AUTHORITIES, key = "#userId")
    public void evict(Long userId) {
        // the annotation does the work
    }

    /** After a change to a role's permissions or a group's roles: every user may be affected. */
    @CacheEvict(cacheNames = CacheConfig.USER_AUTHORITIES, allEntries = true)
    public void evictAll() {
        // the annotation does the work
    }
}
