package com.dilaw.mohe.ums.model;

import java.util.Optional;

/**
 * The five administrative tiers of the staff model, as explicit roles.
 *
 * <p>Security plan R-12 (6 Sep 2026). Until this pass the tier was inferred from
 * three flags on {@code UMSCORE.Users} — {@code IsSuperAdmin}, {@code IsAdmin},
 * {@code IsMinistryUser} — and from how many countries an {@code IsAdmin}
 * account happened to hold: {@code admin} holding every attaché was "the
 * ministry admin", {@code jo_admin} holding one was "a country admin", and
 * nothing in the data said so. {@code JwtAuthFilter} then turned any of the
 * three flags into {@code ROLE_ADMIN}, which every write expression accepted,
 * so every admin of every kind had every permission.
 *
 * <p>Now each tier is a system role in {@code UMSCORE.Roles} (V094 creates
 * {@code MINISTRY_ADMIN}, {@code MINISTRY_STAFF} and {@code COUNTRY_ADMIN};
 * {@code SUPER_ADMIN} already existed), each carries a permission bundle, and
 * the JWT filter emits {@code ROLE_<tier>} from role membership rather than
 * from flags. The flags stay as columns during the transition and
 * {@code UmsUserService} keeps them in step with the role assignments, so code
 * that still reads a flag ({@code CountryScopeService.getScopeForUser}) sees
 * the same answer.
 *
 * <p>Who administers whom (plan B.3), enforced by {@code UmsPrivilegeGuard}:
 * <ul>
 *   <li>{@link #SUPER_ADMIN} — everyone, including other super admins; the only
 *       tier that restores backups or uses an audited approval bypass;</li>
 *   <li>{@link #MINISTRY_ADMIN} — every account below a super admin, in any
 *       country; reads and approves Wafar, never records it (N-04);</li>
 *   <li>{@link #MINISTRY_STAFF} — reads every country; administers nobody but
 *       themselves (N-01); never writes Wafar;</li>
 *   <li>{@link #COUNTRY_ADMIN} — their own countries' staff and data in full,
 *       Wafar included (N-02);</li>
 *   <li>{@link #COUNTRY_STAFF} — not a role: the absence of the four above.</li>
 * </ul>
 */
public enum AdminTier {

    SUPER_ADMIN, MINISTRY_ADMIN, MINISTRY_STAFF, COUNTRY_ADMIN, COUNTRY_STAFF;

    /** The {@code UMSCORE.Roles.RoleName} that confers this tier; none for country staff. */
    public Optional<String> roleName() {
        return this == COUNTRY_STAFF ? Optional.empty() : Optional.of(name());
    }

    /** The Spring Security authority the JWT filter emits for this tier. */
    public String authority() {
        return "ROLE_" + name();
    }

    /** Acts for the ministry rather than for one attaché: read-only on Wafar (N-04). */
    public boolean ministryLevel() {
        return this == SUPER_ADMIN || this == MINISTRY_ADMIN || this == MINISTRY_STAFF;
    }

    /** Sees every staff account whatever its countries (StaffCountrySpec clause 1). */
    public boolean seesAllStaff() {
        return ministryLevel();
    }

    /** May create, edit and delete staff accounts within reach (N-01, N-02). */
    public boolean administersStaff() {
        return this == SUPER_ADMIN || this == MINISTRY_ADMIN || this == COUNTRY_ADMIN;
    }

    /** Whether {@code this} may administer an account of tier {@code target} (plan B.3 matrix). */
    public boolean mayAdminister(AdminTier target) {
        return switch (this) {
            case SUPER_ADMIN -> true;
            case MINISTRY_ADMIN -> target != SUPER_ADMIN;
            case COUNTRY_ADMIN -> target == COUNTRY_ADMIN || target == COUNTRY_STAFF;
            case MINISTRY_STAFF, COUNTRY_STAFF -> false;
        };
    }

    /** Whether {@code this} may grant the role that confers {@code granted} (N-02.b). */
    public boolean mayGrant(AdminTier granted) {
        return switch (this) {
            case SUPER_ADMIN -> true;
            case MINISTRY_ADMIN -> granted != SUPER_ADMIN;
            case COUNTRY_ADMIN -> granted == COUNTRY_ADMIN || granted == COUNTRY_STAFF;
            case MINISTRY_STAFF, COUNTRY_STAFF -> false;
        };
    }

    /** The tier a role name confers, if it is one of the four tier roles. */
    public static Optional<AdminTier> fromRoleName(String roleName) {
        if (roleName == null) return Optional.empty();
        for (AdminTier t : values()) {
            if (t != COUNTRY_STAFF && t.name().equals(roleName)) return Optional.of(t);
        }
        return Optional.empty();
    }
}
