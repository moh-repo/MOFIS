package com.dilaw.mohe.ums.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Everything one account can do, and why.
 *
 * <p>Security plan R-13 (6 Sep 2026). The resolved permission set with its
 * provenance (which role, which group, which direct grant), the tier, the
 * countries, the exceptions and their expiry, the separation-of-duties
 * violations, and what the account would have reached under the old
 * {@code ROLE_ADMIN} rule but does not under its tier bundle (the R-12 cutover
 * check). The screen behind it answers an auditor's question without a query.
 */
public record EffectivePermissionReport(
        Long userId,
        String userName,
        String fullName,
        String status,
        String adminTier,
        boolean isAdmin,
        boolean isSuperAdmin,
        boolean isMinistryUser,
        String positionCode,
        List<CountryRef> countries,
        List<RoleRef> roles,
        List<GroupRef> groups,
        List<PermissionLine> permissions,
        List<DirectGrant> directGrants,
        List<String> syntheticAuthorities,
        List<String> lostVersusLegacyAdmin,
        List<Violation> separationOfDutiesViolations,
        /**
         * Stable note codes, not sentences: {@code MINISTRY_SCOPE},
         * {@code NO_ATTACHE}, {@code SUPER_ADMIN_ALL}, {@code LEGACY_ADMIN},
         * {@code NO_POSITION}. The screen renders each through
         * {@code UMS.USERS.EFF_NOTE_<code>}, so the reader gets them in their own
         * language; English prose here would have been English in the Arabic UI
         * too, which is what it was until 7 September 2026.
         */
        List<String> notes) {

    /**
     * {@code name} is the Arabic name (the default {@code CountryNameService}
     * serves), {@code nameEn} the English one. Both, because the report is the
     * screen's only source for this list and it was previously Arabic-only —
     * so the English UI tagged an account's attachés in Arabic.
     */
    public record CountryRef(Integer id, String name, String nameEn) {}

    /**
     * {@code via}: {@code DIRECT}, or {@code GROUP:<group name>}.
     *
     * <p>{@code nameAr} is {@code UMSCORE.Roles.RoleNameAr}. Every seeded role has
     * one, so an Arabic reader sees a role named in Arabic rather than the
     * {@code MINISTRY_ADMIN} the column happens to store.
     */
    public record RoleRef(Long id, String name, String nameAr, String via, boolean tierRole) {}

    /** {@code nameAr} is {@code UMSCORE.UserGroups.GroupNameAr}. */
    public record GroupRef(Long id, String name, String nameAr) {}

    /**
     * One effective code. {@code sources}: {@code ROLE:<role>},
     * {@code GROUP:<group>/ROLE:<role>}, {@code DIRECT (until <date>)},
     * {@code SUPER_ADMIN} (the flag short-circuit).
     *
     * <p>{@code description} is the English one from {@code PermissionCatalog};
     * {@code descriptionAr} is {@code UMSCORE.Permissions.PermissionNameAr}, the
     * only Arabic the database holds for a code. They come from different places
     * because they are different things — a sentence and a name — and the screen
     * shows whichever matches the reader's language, falling back to the English
     * when a code has no Arabic name (one of 202 on dev). Sending both keeps the
     * choice in the browser, where the language is known.
     */
    public record PermissionLine(String code, String description, String descriptionAr,
                                 boolean enforced, List<String> sources) {}

    public record DirectGrant(Long permissionId, String code, String reason, Long grantedBy, LocalDateTime grantedAt,
                              LocalDateTime expiresAt, boolean effective, boolean configuration) {}

    public record Violation(String codeA, String codeB, String reason) {}

    /** The symmetric difference of two accounts' effective codes. */
    public record Diff(Long userA, String userNameA, Long userB, String userNameB,
                       List<String> onlyInA, List<String> onlyInB, List<String> common) {}

    /** A live exception across accounts, for the review list. */
    public record ExceptionRow(Long userId, String userName, String code, String reason, Long grantedBy,
                               LocalDateTime grantedAt, LocalDateTime expiresAt, boolean expired,
                               boolean configuration) {}
}
