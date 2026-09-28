import { Injectable } from '@angular/core';
import { UmsAuthService } from './ums-auth.service';
import { AuthService } from './auth.service';
import { PermissionMap } from '../api/ums';

@Injectable({
    providedIn: 'root'
})
export class UmsPermissionService {

    constructor(
        private authService: UmsAuthService,
        private oldAuthService: AuthService
    ) { }

    /**
     * Check if user has a specific permission
     * @param permissionCode Permission code (e.g., 'STUDENTS.VIEW')
     * @returns true if user has the permission
     */
    /** Write actions on data one attaché owns outright (N-04): never shown to a ministry-level account. */
    private static readonly COUNTRY_OWNED_WRITES = /^WAFAR\.(ADD|EDIT|DELETE|APPROVE)$/;
    /** Staff administration (N-01): never shown to ministry staff (IsMinistryUser without IsAdmin). */
    private static readonly STAFF_ADMIN_WRITES = /^UMS_ADMIN\.(ADD|EDIT|DELETE)$/;

    hasPermission(permissionCode: string): boolean {
        // The two business rules the server enforces before any code is consulted
        // (security plan N-01 and N-04, 6 Sep 2026). Mirrored here so the buttons
        // that would be refused are not drawn; the server refuses regardless.
        if (UmsPermissionService.COUNTRY_OWNED_WRITES.test(permissionCode) && this.authService.isMinistryLevel) {
            return false;
        }
        if (UmsPermissionService.STAFF_ADMIN_WRITES.test(permissionCode) && this.authService.isMinistryStaff()) {
            return false;
        }

        // Super admin has all permissions (the server returns every code for it anyway).
        if (this.authService.isSuperAdmin()) {
            return true;
        }
        // R-12 (6 Sep 2026): an administrator is no longer "everything". The
        // server sends the codes of the tier bundle plus the account's other
        // roles, and the list below is what decides — the same rule as the
        // server's, which no longer accepts ROLE_ADMIN.

        // If UMS is not logged in, check old auth system for admin role only
        if (!this.authService.isLoggedIn() && this.oldAuthService.isLoggedIn()) {
            const currentUser = this.oldAuthService.currentUserValue;
            if (currentUser && (currentUser.role?.roleName === 'ADMIN' || currentUser.role?.roleName === 'SUPER_ADMIN')) {
                return true;
            }
            // Non-admin with only old auth: no UMS permissions, deny by default
            return false;
        }

        // UMS user: must have permission explicitly in list (guard against undefined/stale)
        const permissions = this.authService.permissions || [];
        return permissions.includes(permissionCode);
    }

    /**
     * Any-of check: true when the user holds at least one of the codes.
     * Admin short-circuits are inherited from hasPermission; an empty list is false.
     * @param permissionCodes Permission codes (e.g. ['DASHBOARD.VIEW', 'DASHBOARD_FINANCE.VIEW'])
     */
    hasAnyPermission(permissionCodes: string[]): boolean {
        return (permissionCodes || []).some(code => this.hasPermission(code));
    }

    /**
     * Check if user can access a page
     * @param pageCode Page code (e.g., 'STUDENTS')
     * @returns true if user can VIEW the page
     */
    canAccessPage(pageCode: string): boolean {
        return this.hasPermission(`${pageCode}.VIEW`);
    }

    /**
     * Check if user can perform an action on a page
     * @param pageCode Page code
     * @param action Action type (SEARCH, VIEW, ADD, EDIT, DELETE, EXPORT)
     * @returns true if user has the permission
     */
    canPerformAction(pageCode: string, action: string): boolean {
        return this.hasPermission(`${pageCode}.${action}`);
    }

    /**
     * Check if user can view a page
     */
    canView(pageCode: string): boolean {
        return this.canPerformAction(pageCode, 'VIEW');
    }

    /**
     * Check if user can add items on a page
     */
    canAdd(pageCode: string): boolean {
        return this.canPerformAction(pageCode, 'ADD');
    }

    /**
     * Check if user can edit items on a page
     */
    canEdit(pageCode: string): boolean {
        return this.canPerformAction(pageCode, 'EDIT');
    }

    /**
     * Check if user can delete items on a page
     */
    canDelete(pageCode: string): boolean {
        return this.canPerformAction(pageCode, 'DELETE');
    }

    /**
     * Check if user can search on a page
     */
    canSearch(pageCode: string): boolean {
        return this.canPerformAction(pageCode, 'SEARCH');
    }

    /**
     * Check if user can export from a page
     */
    canExport(pageCode: string): boolean {
        return this.canPerformAction(pageCode, 'EXPORT');
    }

    /**
     * Get all actions available for a page
     * @param pageCode Page code
     * @returns Array of available actions
     */
    getPageActions(pageCode: string): string[] {
        return this.authService.permissionMap[pageCode] || [];
    }

    /**
     * Get all permission codes for the current user
     * @returns Array of permission codes
     */
    getAllPermissions(): string[] {
        return this.authService.permissions;
    }

    /**
     * Get the permission map for the current user
     * @returns Permission map (page code -> actions)
     */
    getPermissionMap(): PermissionMap {
        return this.authService.permissionMap;
    }

    /**
     * Check if user has access to any country (not all countries)
     */
    hasCountryRestriction(): boolean {
        return this.authService.countryIds.length > 0;
    }

    /**
     * Get country IDs the user has access to
     * @returns Array of country IDs (empty means all countries)
     */
    getAllowedCountryIds(): number[] {
        return this.authService.countryIds;
    }

    /**
     * Check if user has access to a specific country
     * @param countryId Country ID to check
     * @returns true if user has access
     */
    hasCountryAccess(countryId: number): boolean {
        // Super admin or user with no country restrictions has access to all
        if (this.authService.isSuperAdmin() || this.authService.countryIds.length === 0) {
            return true;
        }
        return this.authService.countryIds.includes(countryId);
    }

    /**
     * Check if user can view a field on a page
     * @param pageCode Page code
     * @param fieldCode Field code
     * @returns true if user can view the field
     */
    canViewField(pageCode: string, fieldCode: string): boolean {
        return this.hasPermission(`${pageCode}.${fieldCode}.VIEW`);
    }

    /**
     * Check if user can edit a field on a page
     * @param pageCode Page code
     * @param fieldCode Field code
     * @returns true if user can edit the field
     */
    canEditField(pageCode: string, fieldCode: string): boolean {
        return this.hasPermission(`${pageCode}.${fieldCode}.EDIT`);
    }
}
