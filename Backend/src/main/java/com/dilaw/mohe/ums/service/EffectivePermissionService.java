package com.dilaw.mohe.ums.service;

import com.dilaw.mohe.config.PermissionCatalog;
import com.dilaw.mohe.service.CountryNameService;
import com.dilaw.mohe.ums.dto.EffectivePermissionReport;
import com.dilaw.mohe.ums.dto.EffectivePermissionReport.CountryRef;
import com.dilaw.mohe.ums.dto.EffectivePermissionReport.DirectGrant;
import com.dilaw.mohe.ums.dto.EffectivePermissionReport.ExceptionRow;
import com.dilaw.mohe.ums.dto.EffectivePermissionReport.GroupRef;
import com.dilaw.mohe.ums.dto.EffectivePermissionReport.PermissionLine;
import com.dilaw.mohe.ums.dto.EffectivePermissionReport.RoleRef;
import com.dilaw.mohe.ums.dto.EffectivePermissionReport.Violation;
import com.dilaw.mohe.ums.model.AdminTier;
import com.dilaw.mohe.ums.model.UmsGroup;
import com.dilaw.mohe.ums.model.UmsGroupRole;
import com.dilaw.mohe.ums.model.UmsPermission;
import com.dilaw.mohe.ums.model.UmsRole;
import com.dilaw.mohe.ums.model.UmsUser;
import com.dilaw.mohe.ums.model.UmsUserCountry;
import com.dilaw.mohe.ums.model.UmsUserGroup;
import com.dilaw.mohe.ums.model.UmsUserPermission;
import com.dilaw.mohe.ums.model.UmsUserRole;
import com.dilaw.mohe.ums.repository.UmsGroupRoleRepository;
import com.dilaw.mohe.ums.repository.UmsPermissionRepository;
import com.dilaw.mohe.ums.repository.UmsUserCountryRepository;
import com.dilaw.mohe.ums.repository.UmsUserGroupRepository;
import com.dilaw.mohe.ums.repository.UmsUserPermissionRepository;
import com.dilaw.mohe.ums.repository.UmsUserRoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Builds the {@link EffectivePermissionReport} (security plan R-13).
 *
 * <p>Reads through {@link UmsUserService#require} so a caller sees the report
 * of an account they may administer and nobody else's; the country admin gets
 * their own staff, the ministry every account.
 */
@Service
public class EffectivePermissionService {

    @Autowired private UmsUserService userService;
    @Autowired private UmsUserRoleRepository userRoleRepository;
    @Autowired private UmsUserGroupRepository userGroupRepository;
    @Autowired private UmsGroupRoleRepository groupRoleRepository;
    @Autowired private UmsPermissionRepository permissionRepository;
    @Autowired private UmsUserPermissionRepository userPermissionRepository;
    @Autowired private UmsUserCountryRepository userCountryRepository;
    @Autowired private CountryNameService countryNameService;
    @Autowired private AdminTierService adminTierService;
    @Autowired private SeparationOfDutiesService separationOfDuties;

    private static final Map<String, String> DESCRIPTIONS = new LinkedHashMap<>();
    static {
        for (PermissionCatalog.Entry e : PermissionCatalog.ENTRIES) DESCRIPTIONS.put(e.code(), e.description());
    }

    @Transactional(readOnly = true)
    public EffectivePermissionReport reportFor(Long userId) {
        UmsUser user = userService.require(userId);
        return build(user);
    }

    /** The caller's own report, unscoped: everyone may see what they can do. */
    @Transactional(readOnly = true)
    public EffectivePermissionReport reportForSelf(UmsUser user) {
        return build(user);
    }

    @Transactional(readOnly = true)
    public EffectivePermissionReport.Diff diff(Long a, Long b) {
        EffectivePermissionReport ra = reportFor(a);
        EffectivePermissionReport rb = reportFor(b);
        Set<String> ca = new TreeSet<>(ra.permissions().stream().map(PermissionLine::code).toList());
        Set<String> cb = new TreeSet<>(rb.permissions().stream().map(PermissionLine::code).toList());
        List<String> onlyA = ca.stream().filter(c -> !cb.contains(c)).toList();
        List<String> onlyB = cb.stream().filter(c -> !ca.contains(c)).toList();
        List<String> common = ca.stream().filter(cb::contains).toList();
        return new EffectivePermissionReport.Diff(a, ra.userName(), b, rb.userName(), onlyA, onlyB, common);
    }

    /** R-14: every live direct grant among the accounts the caller can see. */
    @Transactional(readOnly = true)
    public List<ExceptionRow> exceptions() {
        Set<Long> visible = new LinkedHashSet<>(userService.visibleUsers().stream().map(UmsUser::getId).toList());
        List<ExceptionRow> rows = new ArrayList<>();
        for (UmsUserPermission up : userPermissionRepository.findAllActive()) {
            if (!visible.contains(up.getUser().getId())) continue;
            String code = up.getPermission().getPermissionCode();
            rows.add(new ExceptionRow(up.getUser().getId(), up.getUser().getUserName(), code, up.getReason(),
                    up.getGrantedBy(), up.getGrantedAt(), up.getExpiresAt(), !up.isEffective(),
                    PermissionCatalog.isPerUserConfiguration(code)));
        }
        return rows;
    }

    /** R-15: the accounts the caller can see that hold both halves of an active pair. */
    @Transactional(readOnly = true)
    public Map<String, List<Violation>> violations() {
        Map<String, List<Violation>> out = new TreeMap<>();
        for (UmsUser u : userService.visibleUsers()) {
            if (adminTierService.tierOf(u.getId()) == AdminTier.SUPER_ADMIN) continue;
            List<Violation> v = separationOfDuties.violationsIn(separationOfDuties.effectiveCodes(u.getId())).stream()
                    .map(x -> new Violation(x.codeA(), x.codeB(), x.reason())).toList();
            if (!v.isEmpty()) out.put(u.getUserName(), v);
        }
        return out;
    }

    private EffectivePermissionReport build(UmsUser user) {
        Long userId = user.getId();
        Set<String> roleNames = adminTierService.roleNamesOf(userId);
        AdminTier tier = adminTierService.tierOf(user, roleNames);
        // Stable codes rather than sentences: the screen translates each through
        // UMS.USERS.EFF_NOTE_<code>. English prose here reached the Arabic UI as
        // English, which is what it did until 7 September 2026.
        List<String> notes = new ArrayList<>();

        // Countries
        List<CountryRef> countries = new ArrayList<>();
        Map<Integer, String> names = countryNameService.nameMap();
        Map<Integer, String> namesEn = countryNameService.nameMap(false);
        if (tier.ministryLevel()) {
            for (Integer id : countryNameService.allIds()) {
                countries.add(new CountryRef(id, names.getOrDefault(id, String.valueOf(id)),
                        namesEn.getOrDefault(id, String.valueOf(id))));
            }
            notes.add("MINISTRY_SCOPE");
        } else {
            for (UmsUserCountry uc : userCountryRepository.findByUserIdAndIsActiveTrue(userId)) {
                Integer id = uc.getCountryId();
                countries.add(new CountryRef(id, names.getOrDefault(id, String.valueOf(id)),
                        namesEn.getOrDefault(id, String.valueOf(id))));
            }
            if (countries.isEmpty()) notes.add("NO_ATTACHE");
        }

        // Roles with provenance, and the code -> sources map
        Map<String, Set<String>> sources = new TreeMap<>();
        List<RoleRef> roles = new ArrayList<>();
        List<GroupRef> groups = new ArrayList<>();
        for (UmsUserRole ur : userRoleRepository.findByUserIdAndIsActiveTrue(userId)) {
            UmsRole r = ur.getRole();
            if (!Boolean.TRUE.equals(r.getIsActive())) continue;
            roles.add(new RoleRef(r.getId(), r.getRoleName(), r.getRoleNameAr(), "DIRECT",
                    AdminTier.fromRoleName(r.getRoleName()).isPresent()));
            for (UmsPermission p : permissionRepository.findPermissionsByRoleId(r.getId())) {
                sources.computeIfAbsent(p.getPermissionCode(), k -> new LinkedHashSet<>()).add("ROLE:" + r.getRoleName());
            }
        }
        for (UmsUserGroup ug : userGroupRepository.findByUserIdAndIsActiveTrue(userId)) {
            UmsGroup g = ug.getGroup();
            if (!Boolean.TRUE.equals(g.getIsActive())) continue;
            groups.add(new GroupRef(g.getId(), g.getGroupName(), g.getGroupNameAr()));
            for (UmsGroupRole gr : groupRoleRepository.findByGroupIdAndIsActiveTrue(g.getId())) {
                UmsRole r = gr.getRole();
                if (!Boolean.TRUE.equals(r.getIsActive())) continue;
                roles.add(new RoleRef(r.getId(), r.getRoleName(), r.getRoleNameAr(), "GROUP:" + g.getGroupName(),
                        AdminTier.fromRoleName(r.getRoleName()).isPresent()));
                for (UmsPermission p : permissionRepository.findPermissionsByRoleId(r.getId())) {
                    sources.computeIfAbsent(p.getPermissionCode(), k -> new LinkedHashSet<>())
                            .add("GROUP:" + g.getGroupName() + "/ROLE:" + r.getRoleName());
                }
            }
        }
        List<DirectGrant> directGrants = new ArrayList<>();
        for (UmsUserPermission up : userPermissionRepository.findByUserIdAndIsActiveTrue(userId)) {
            String code = up.getPermission().getPermissionCode();
            boolean configuration = PermissionCatalog.isPerUserConfiguration(code);
            directGrants.add(new DirectGrant(up.getPermission().getId(), code, up.getReason(), up.getGrantedBy(),
                    up.getGrantedAt(), up.getExpiresAt(), up.isEffective(), configuration));
            if (up.isEffective() && Boolean.TRUE.equals(up.getPermission().getIsActive())) {
                sources.computeIfAbsent(code, k -> new LinkedHashSet<>())
                        .add("DIRECT" + (up.getExpiresAt() == null ? "" : " (until " + up.getExpiresAt().toLocalDate() + ")"));
            }
        }
        if (tier == AdminTier.SUPER_ADMIN) {
            for (UmsPermission p : permissionRepository.findByIsActiveTrue()) {
                sources.computeIfAbsent(p.getPermissionCode(), k -> new LinkedHashSet<>()).add("SUPER_ADMIN");
            }
            notes.add("SUPER_ADMIN_ALL");
        }

        // The Arabic name of every code in one read, so the screen can label a
        // permission in the reader's language. PermissionCatalog is English-only
        // and is a build-time constant; the Arabic lives in the database, which is
        // also where the permission-management screen edits it.
        Map<String, String> arabicNames = new LinkedHashMap<>();
        for (UmsPermission p : permissionRepository.findByIsActiveTrue()) {
            String ar = p.getPermissionNameAr();
            if (ar != null && !ar.isBlank()) arabicNames.put(p.getPermissionCode(), ar);
        }

        List<PermissionLine> permissions = new ArrayList<>();
        for (Map.Entry<String, Set<String>> e : sources.entrySet()) {
            permissions.add(new PermissionLine(e.getKey(), DESCRIPTIONS.get(e.getKey()),
                    arabicNames.get(e.getKey()),
                    PermissionCatalog.contains(e.getKey()), new ArrayList<>(e.getValue())));
        }

        // R-12 cutover check: what ROLE_ADMIN used to give this account that the tier bundle does not.
        List<String> lost = new ArrayList<>();
        if (tier != AdminTier.COUNTRY_STAFF && tier != AdminTier.SUPER_ADMIN) {
            Set<String> held = sources.keySet();
            for (String code : PermissionCatalog.codes()) {
                if (!held.contains(code)) lost.add(code);
            }
            if (!lost.isEmpty()) {
                notes.add("LEGACY_ADMIN");
            }
        }

        List<String> synthetic = new ArrayList<>();
        if (tier != AdminTier.COUNTRY_STAFF) synthetic.add(tier.authority());

        List<Violation> violations = tier == AdminTier.SUPER_ADMIN ? List.of()
                : separationOfDuties.violationsIn(sources.keySet()).stream()
                        .map(v -> new Violation(v.codeA(), v.codeB(), v.reason())).toList();

        if (user.getPositionCode() == null && (roleNames.stream().anyMatch(n -> n.toUpperCase().contains("MANAGER"))
                || roles.stream().anyMatch(r -> r.name().contains("REVIEWER")))) {
            notes.add("NO_POSITION");
        }

        return new EffectivePermissionReport(userId, user.getUserName(), user.getFullName(), user.getStatus(),
                tier.name(), Boolean.TRUE.equals(user.getIsAdmin()), Boolean.TRUE.equals(user.getIsSuperAdmin()),
                Boolean.TRUE.equals(user.getIsMinistryUser()), user.getPositionCode(), countries, roles, groups,
                permissions, directGrants, synthetic, lost, violations, notes);
    }
}
