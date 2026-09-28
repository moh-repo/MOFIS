/**
 * Shape shared by both sidebar layouts.
 *
 * Two trees ship: `menu-classic.ts` is the menu as it has always been, and
 * `menu-proposed.ts` is the reorganised one. A user picks between them in the
 * layout config drawer. Neither file may import the other — the whole point of
 * splitting them is that a change to one cannot move a row in the other.
 *
 * Both are built from a translate function rather than from stored keys, so a
 * language switch rebuilds the tree exactly as it did before the split.
 */
export interface AppMenuItem {
    label?: string;
    icon?: string;
    routerLink?: string[];
    queryParams?: { [k: string]: any };
    routerLinkActiveOptions?: any;
    items?: AppMenuItem[];
    separator?: boolean;
    visible?: boolean;
    class?: string;
    /** Escape hatch: stands in for the map entry of this row's own route; the routes above it still apply. */
    pageCode?: string | string[];
    /**
     * A raw permission code, checked as-is.
     *
     * `pageCode` can only ever ask "can this person VIEW page X", because that
     * is what canView() appends. Some rows lead to a screen the backend guards
     * with an action permission instead — notification settings requires
     * NOTIFICATIONS.SETTINGS, not NOTIFICATIONS.VIEW — and gating those on the
     * page's VIEW right shows the row to people the server will refuse.
     */
    permission?: string;
    /** Shown to the super admin only, whatever the permissions (review M2: system settings). */
    superAdminOnly?: boolean;
    /** Shown to the super admin and the ministry admin only (DEC-15, M15: the admin tool pages). */
    ministryAdminOnly?: boolean;
}

/** A tree is built on demand so it can pick up the active language. */
export type MenuFactory = (t: (key: string) => string) => AppMenuItem[];

/** Which sidebar a person is looking at. */
export type MenuVariant = 'classic' | 'proposed';
