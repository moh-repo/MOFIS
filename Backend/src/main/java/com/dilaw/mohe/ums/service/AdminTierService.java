package com.dilaw.mohe.ums.service;

import com.dilaw.mohe.service.CountryNameService;
import com.dilaw.mohe.ums.model.AdminTier;
import com.dilaw.mohe.ums.model.UmsRole;
import com.dilaw.mohe.ums.model.UmsUser;
import com.dilaw.mohe.ums.model.UmsUserCountry;
import com.dilaw.mohe.ums.repository.UmsRoleRepository;
import com.dilaw.mohe.ums.repository.UmsUserCountryRepository;
import com.dilaw.mohe.ums.repository.UmsUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Which {@link AdminTier} an account is in.
 *
 * <p>Security plan R-12. Reads the tier roles first ({@code SUPER_ADMIN},
 * {@code MINISTRY_ADMIN}, {@code MINISTRY_STAFF}, {@code COUNTRY_ADMIN}, held
 * directly or through a group) and falls back to the account flags only when
 * the account holds none of them: the state of a database on which V094 has
 * not yet run, or of an account created by a path that predates it. The
 * fallback is the inference the phase-1 code made ({@code CountryScopeService
 * .isMinistryLevelUser}): {@code IsSuperAdmin} is a super admin; {@code IsAdmin}
 * holding every study country is a ministry admin; {@code IsMinistryUser}
 * without {@code IsAdmin} is ministry staff; {@code IsAdmin} otherwise is a
 * country admin.
 *
 * <p>The super admin tier comes from the {@code IsSuperAdmin} flag alone. The
 * {@code SUPER_ADMIN} role carries the bundle (every code) but does not confer
 * the tier: on the development database the ordinary {@code admin} account
 * holds that role without the flag, and a stray role row must not hand out
 * the audited bypasses. The flag is the break-glass column, set by a super
 * admin editing the account, and a super admin locked out of their own tier
 * by a missing role row is the failure mode this class must not have.
 *
 * <p>Deliberately depends on repositories only. {@code CountryScopeService},
 * {@code UmsPrivilegeGuard} and {@code StaffCountrySpec} all ask this question,
 * and a dependency back on any of them would be a cycle.
 */
@Service
public class AdminTierService {

    @Autowired private UmsUserRepository userRepository;
    @Autowired private UmsRoleRepository roleRepository;
    @Autowired private UmsUserCountryRepository userCountryRepository;
    @Autowired private CountryNameService countryNameService;

    public AdminTier tierOf(Long userId) {
        if (userId == null) return AdminTier.COUNTRY_STAFF;
        UmsUser user = userRepository.findById(userId).orElse(null);
        return user == null ? AdminTier.COUNTRY_STAFF : tierOf(user, roleNamesOf(userId));
    }

    /** The same, when the caller already has the role names (the authority cache does). */
    public AdminTier tierOf(UmsUser user, Set<String> roleNames) {
        if (user == null || Boolean.TRUE.equals(user.getIsDeleted())) return AdminTier.COUNTRY_STAFF;
        if (Boolean.TRUE.equals(user.getIsSuperAdmin())) {
            return AdminTier.SUPER_ADMIN;
        }
        if (roleNames.contains(AdminTier.MINISTRY_ADMIN.name())) return AdminTier.MINISTRY_ADMIN;
        if (roleNames.contains(AdminTier.MINISTRY_STAFF.name())) return AdminTier.MINISTRY_STAFF;
        if (roleNames.contains(AdminTier.COUNTRY_ADMIN.name())) return AdminTier.COUNTRY_ADMIN;
        return fromFlags(user);
    }

    /** Active role names, direct and through groups. */
    public Set<String> roleNamesOf(Long userId) {
        Set<String> names = new LinkedHashSet<>();
        for (UmsRole r : roleRepository.findRolesByUserIdDirect(userId)) names.add(r.getRoleName());
        for (UmsRole r : roleRepository.findRolesByUserIdThroughGroups(userId)) names.add(r.getRoleName());
        return names;
    }

    private AdminTier fromFlags(UmsUser user) {
        boolean admin = Boolean.TRUE.equals(user.getIsAdmin());
        if (Boolean.TRUE.equals(user.getIsMinistryUser()) && !admin) return AdminTier.MINISTRY_STAFF;
        if (!admin) return AdminTier.COUNTRY_STAFF;
        List<Integer> all = countryNameService.allIds();
        if (all.isEmpty()) return AdminTier.COUNTRY_ADMIN;
        Set<Integer> held = userCountryRepository.findByUserIdAndIsActiveTrue(user.getId()).stream()
                .map(UmsUserCountry::getCountryId)
                .collect(Collectors.toSet());
        return held.containsAll(all) ? AdminTier.MINISTRY_ADMIN : AdminTier.COUNTRY_ADMIN;
    }
}
