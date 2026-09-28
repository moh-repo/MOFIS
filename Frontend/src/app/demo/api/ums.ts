// UMS User Interface
export interface UmsUser {
    id: number;
    userName: string;
    email: string;
    phone?: string;
    firstName: string;
    lastName: string;
    status: 'ACTIVE' | 'INACTIVE' | 'LOCKED';
    isAdmin: boolean;
    isSuperAdmin: boolean;
    isMinistryUser?: boolean;
    failedLoginAttempts: number;
    /** Null until the account has signed in at least once. */
    lastLoginAt: string | null;
    /** Null on an account whose password has never been changed since it was created. */
    passwordChangedAt?: string | null;
    /** V121 (DEC-04): the password was set by an administrator and must be changed before anything else. */
    mustChangePassword?: boolean;
    createdAt: string;
    updatedAt: string;
    /**
     * The attachés this account covers. Read-only, filled in by the backend on
     * list responses; assignment goes through the Manage Countries modal.
     * Empty means the account is awaiting assignment, which is a distinct state
     * from being assigned an attaché — the two are visible under different rules.
     */
    countryIds?: number[];
    countryNames?: string[];
    /** N-03: a MOHECORE.LU_POSITION code; approval levels may require one. */
    positionCode?: string | null;
    /** Which rung of MOHECORE.AUTH_LOCKOUT_POLICY the current run of failures is on; 0 before any lock (V080). */
    lockoutStage?: number;
    /** When a timed lock ends; null with status LOCKED is the ladder's last rung or a manual lock. */
    lockedUntil?: string | null;
    lastFailedLoginAt?: string | null;
    createdBy?: number | null;
    updatedBy?: number | null;
}

/** GET /ums/users/{id}/mfa/status (O-16): whether the account has a second factor. */
export interface UmsMfaStatus {
    enabled: boolean;
    required: boolean;
    enrolledAt: string | null;
    recoveryCodesLeft: number;
    locked: boolean;
}

/**
 * A staff account as the users list, the search and the admins page return it
 * (review H5a, DEC-16, plan W5-01): no lockout internals, password times or raw
 * admin flags. The edit and detail dialogs read the full {@link UmsUser} by id.
 */
export interface UmsUserSummary {
    id: number;
    userName: string;
    firstName: string | null;
    lastName: string | null;
    fullName: string | null;
    email: string | null;
    status: 'ACTIVE' | 'INACTIVE' | 'LOCKED';
    /** From the account's flags: what the list's tag shows. */
    privilege: 'SUPER_ADMIN' | 'ADMIN' | 'USER';
    countryNames: string[];
    lastLoginAt: string | null;
}

/** A staff account offered as a choice: the approval configuration's named approvers (W5-01). */
export interface UmsUserOption {
    id: number;
    userName: string;
    firstName: string | null;
    lastName: string | null;
    fullName: string | null;
    positionCode: string | null;
    countryNames: string[];
}

/**
 * What the browser is allowed to keep about the signed-in account, and the type
 * {@code UmsAuthService} hands to the rest of the app for "who am I".
 *
 * <p>Deliberately smaller than {@link UmsUser}. The login reply carries the whole
 * account record — email, phone, failed-login count, the password-changed and
 * last-login timestamps — and all of it used to be written to `localStorage` and
 * sit there until logout. None of it was ever read: across the application the
 * signed-in account is consulted for `id`, `isAdmin`, `isSuperAdmin` and
 * `isMinistryUser` and nothing else. Persisting the rest bought nothing and put
 * an administrator's contact details and security metadata one DevTools tab, one
 * browser extension or one injected script away.
 *
 * <p>These five fields are not a security boundary and are not treated as one —
 * the flags decide which menu items render, never which requests succeed; the
 * server re-reads the account's real tier and permissions from the database on
 * every request (`JwtAuthFilter` → `UserAuthorityCache`). Editing them here
 * changes the menu and nothing behind it.
 *
 * <p>Anything else about the account comes from `/ums/auth/me` on demand, which
 * is a request the session is entitled to make anyway.
 */
export interface SessionUser {
    id: number;
    userName: string;
    isAdmin: boolean;
    isSuperAdmin: boolean;
    isMinistryUser?: boolean;
    mustChangePassword?: boolean;
}

/** Narrows a login/profile reply to the fields {@link SessionUser} permits to persist. */
export function toSessionUser(user: UmsUser | SessionUser | null | undefined): SessionUser | null {
    if (!user) {
        return null;
    }
    return {
        id: user.id,
        userName: user.userName,
        isAdmin: user.isAdmin,
        isSuperAdmin: user.isSuperAdmin,
        isMinistryUser: user.isMinistryUser ?? false,
        mustChangePassword: user.mustChangePassword === true
    };
}

// UMS Group Interface
export interface UmsGroup {
    id: number;
    groupName: string;
    groupNameAr: string;
    description: string;
    descriptionAr: string;
    isActive: boolean;
    isSystem: boolean;
    createdAt: string;
    updatedAt: string;
}

// UMS Role Interface
export interface UmsRole {
    id: number;
    roleName: string;
    roleNameAr: string;
    description: string;
    descriptionAr: string;
    isActive: boolean;
    isSystem: boolean;
    priority: number;
    createdAt: string;
    updatedAt: string;
}

// UMS Permission Interface
export interface UmsPermission {
    id: number;
    permissionCode: string;
    permissionName: string;
    permissionNameAr: string;
    resourceType: 'PAGE' | 'SECTION' | 'FIELD' | 'REPORT';
    resourceId: number;
    actionType: 'SEARCH' | 'VIEW' | 'ADD' | 'EDIT' | 'DELETE' | 'EXPORT';
    description: string;
    isActive: boolean;
    createdAt: string;
    updatedAt: string;
}

// UMS Page Interface
export interface UmsPage {
    id: number;
    pageCode: string;
    pageName: string;
    pageNameAr: string;
    route: string;
    icon: string;
    parentPageId: number | null;
    description?: string;
    displayOrder: number;
    isActive: boolean;
    isVisible: boolean;
    createdAt: string;
    updatedAt: string;
}

// UMS Page Section Interface
export interface UmsPageSection {
    id: number;
    pageId: number;
    sectionCode: string;
    sectionName: string;
    sectionNameAr: string;
    displayOrder: number;
    isActive: boolean;
    createdAt: string;
    updatedAt: string;
}

// UMS Field Interface
export interface UmsField {
    id: number;
    pageId: number;
    sectionId: number | null;
    fieldCode: string;
    fieldName: string;
    fieldNameAr: string;
    fieldType: string;
    isSensitive: boolean;
    isActive: boolean;
    createdAt: string;
    updatedAt: string;
}

// UMS Report Interface
/**
 * A row of UMSCORE.UMS_Reports — the report catalogue. Hubs and landing cards
 * have no parent; a hub's cards carry the hub's code as `parentCode`.
 */
export interface UmsReport {
    id: number;
    reportCode: string;
    reportName: string;
    reportNameAr: string;
    description: string;
    descriptionAr: string;
    category: string;
    route: string;
    isActive: boolean;
    /** The hub this card belongs to; null for hubs and landing cards. */
    parentCode: string | null;
    /** PrimeIcons name, `pi-…`. */
    icon: string | null;
    /** Colour token as the templates use it (`cyan`, `green-600`). */
    color: string | null;
    /** Query string the card navigates with, e.g. `doc=id-card`. */
    queryParams: string | null;
    /** Page code whose VIEW opens the card. */
    pageCode: string | null;
    /** Comma-separated page codes; holding any one opens the card. */
    permissionCodes: string | null;
    sortOrder: number | null;
    createdAt: string;
    updatedAt: string;
}

// UMS User Country Interface
export interface UmsUserCountry {
    id: number;
    userId: number;
    countryId: number;
    assignedAt: string;
    assignedBy: number;
    isActive: boolean;
}

// Login Response Interface (backend may include token for JWT auth)
export interface LoginResponse {
    token?: string;
    user: UmsUser;
    permissions: string[];
    permissionMap: { [key: string]: string[] };
    countryIds: number[];
    isMinistryUser?: boolean;
    /** O-25/N-04: super admin, ministry user, or admin holding every country; no Wafar writes, no attaché-staff administration for ministry staff. */
    isMinistryLevel?: boolean;
    /** R-12: the explicit administrator tier (a role since V094), replacing the three flags. */
    adminTier?: AdminTier;
    /** O-16: the password was accepted and a code is needed; `mfaToken` names the pending sign-in. */
    mfaRequired?: boolean;
    /** O-16: the password was accepted and the account must enrol a second factor first. */
    mfaEnrolmentRequired?: boolean;
    mfaToken?: string;
    username?: string;
    /** O-16: returned once, by the enrolment that just completed. */
    recoveryCodes?: string[];
}

/** S-04: one period's access review of one attaché (countryId null: the ministry's staff). */
export interface AccessReview {
    id: number;
    period: string;
    countryId: number | null;
    countryName: string | null;
    status: 'OPEN' | 'CLOSED';
    openedAt: string;
    dueAt: string;
    closedAt: string | null;
    total: number;
    undecided: number;
}

export interface AccessReviewSnapshot {
    userName: string;
    fullName: string;
    status: string;
    tier: string;
    positionCode: string | null;
    roles: string[];
    countries: string[];
    lastLoginAt: string | null;
    directGrants: string[];
}

export interface AccessReviewItem {
    id: number;
    userId: number;
    snapshot: AccessReviewSnapshot;
    decision: 'KEEP' | 'CHANGE' | 'REMOVE' | null;
    decidedBy: number | null;
    decidedAt: string | null;
    note: string | null;
    overdue: boolean;
}

/** O-16: one account's second factor as `GET /auth/mfa/status` reports it. */
export interface MfaStatus {
    enabled: boolean;
    required: boolean;
    enrolledAt?: string | null;
    recoveryCodesLeft: number;
    locked: boolean;
}

/** O-16: a fresh secret to put into an authenticator app. */
export interface MfaEnrolment {
    secret: string;
    otpauthUri: string;
    /** A data: URL of the QR code PNG. */
    qrPng: string;
}

/** R-12: the five administrator tiers; COUNTRY_STAFF is the absence of the other four. */
export type AdminTier = 'SUPER_ADMIN' | 'MINISTRY_ADMIN' | 'MINISTRY_STAFF' | 'COUNTRY_ADMIN' | 'COUNTRY_STAFF';

/** R-13: one account's resolved permissions with their provenance. */
export interface EffectivePermissionReport {
    userId: number;
    userName: string;
    fullName: string;
    status: string;
    adminTier: AdminTier;
    isAdmin: boolean;
    isSuperAdmin: boolean;
    isMinistryUser: boolean;
    positionCode: string | null;
    /** `name` is Arabic, `nameEn` English; the screen picks by language. */
    countries: { id: number; name: string; nameEn: string | null }[];
    roles: { id: number; name: string; nameAr: string | null; via: string; tierRole: boolean }[];
    groups: { id: number; name: string; nameAr: string | null }[];
    /** `description` is the English catalogue sentence; `descriptionAr` the database's Arabic name. */
    permissions: { code: string; description: string | null; descriptionAr: string | null; enforced: boolean; sources: string[] }[];
    directGrants: {
        permissionId: number; code: string; reason: string | null; grantedBy: number | null;
        grantedAt: string | null; expiresAt: string | null; effective: boolean; configuration: boolean;
    }[];
    syntheticAuthorities: string[];
    lostVersusLegacyAdmin: string[];
    separationOfDutiesViolations: { codeA: string; codeB: string; reason: string | null }[];
    notes: string[];
}

export interface EffectivePermissionDiff {
    userA: number; userNameA: string; userB: number; userNameB: string;
    onlyInA: string[]; onlyInB: string[]; common: string[];
}

/** R-14: one live direct grant across the accounts the caller can see. */
export interface DirectGrantException {
    userId: number; userName: string; code: string; reason: string | null; grantedBy: number | null;
    grantedAt: string | null; expiresAt: string | null; expired: boolean; configuration: boolean;
}

/** R-14: a direct grant of one account, with reason and expiry. */
export interface UmsDirectGrant {
    permissionId: number; permissionCode: string; reason: string | null; expiresAt: string | null;
    grantedAt: string | null; grantedBy: number | null; effective: boolean;
}

/** R-15: a maker/checker pair and who breaches it. */
export interface SeparationOfDutiesReport {
    pairs: { id: number; codeA: string; codeB: string; reason: string | null; isActive: boolean }[];
    violations: { [userName: string]: { codeA: string; codeB: string; reason: string | null }[] };
}

// Permission Map Type
export type PermissionMap = { [pageCode: string]: string[] };
