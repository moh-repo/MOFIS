import { Directive, Input, TemplateRef, ViewContainerRef, OnInit, OnDestroy } from '@angular/core';
import { UmsPermissionService } from '../service/ums-permission.service';
import { UmsAuthService } from '../service/ums-auth.service';
import { Subscription, merge } from 'rxjs';

/**
 * Structural directive that conditionally includes a template based on permission.
 *
 * Usage:
 *   *hasPermission="'STUDENTS.EDIT'"                      — one code
 *   *hasPermission="'STUDENTS|EDIT'"                      — same, legacy separator
 *   *hasPermission="['DASHBOARD.VIEW', 'DASHBOARD_FINANCE.VIEW']" — any-of: shown when the
 *                                                           user holds at least one of them
 *   *hasPermission="'UMS_ADMIN.EDIT'; adminTools: true"  — the code, and the admin-tools
 *                                                           tier (super / ministry admin, FE-11)
 */
@Directive({
    // Predates the app- prefix rule; renaming it means touching every *hasPermission in the templates.
    // eslint-disable-next-line @angular-eslint/directive-selector
    selector: '[hasPermission]',
    standalone: true
})
export class HasPermissionDirective implements OnInit, OnDestroy {
    private permissionCodes: string[] = [];
    private isShown = false;
    private permissionSubscription?: Subscription;

    constructor(
        private templateRef: TemplateRef<any>,
        private viewContainer: ViewContainerRef,
        private permissionService: UmsPermissionService,
        private authService: UmsAuthService
    ) { }

    @Input()
    set hasPermission(value: string | string[]) {
        // Support both 'PAGE.ACTION' and 'PAGE|ACTION' formats, per element.
        const list = Array.isArray(value) ? value : [value];
        this.permissionCodes = list
            .filter((code): code is string => typeof code === 'string' && code.length > 0)
            .map(code => code.replace('|', '.'));
        this.updateView();
    }

    /**
     * FE-11 (DEC-15): the write actions of the admin-tools tier — pages, reports, API links,
     * fields, and since the server moved them there the permissions, roles and groups
     * (create, edit, delete, assign). The server allows them only to the super admin and
     * the ministry admin, and then only with the UMS_ADMIN.* code named here
     * (MoheSecurityExpressions.ADMIN_TOOLS_ADD / _EDIT / _DELETE). A country admin holds
     * the code for its own staff and would otherwise see buttons that end in a 403.
     *
     *   *hasPermission="'UMS_ADMIN.EDIT'; adminTools: true"
     */
    @Input()
    set hasPermissionAdminTools(value: boolean) {
        this.adminTools = !!value;
        this.updateView();
    }
    private adminTools = false;

    ngOnInit(): void {
        // Subscribe to permission changes to reactively update the view. The tier arrives
        // after the permission list in a profile update, so a tier-gated element also
        // listens for the profile.
        const changes = this.adminTools
            ? merge(this.authService.permissions$, this.authService.profile$)
            : this.authService.permissions$;
        this.permissionSubscription = changes.subscribe(() => {
            this.updateView();
        });
        this.updateView();
    }

    ngOnDestroy(): void {
        if (this.permissionSubscription) {
            this.permissionSubscription.unsubscribe();
        }
    }

    private updateView(): void {
        if (this.permissionCodes.length === 0) {
            return;
        }

        // One code behaves exactly as before; several are an any-of.
        const hasCode = this.permissionCodes.length === 1
            ? this.permissionService.hasPermission(this.permissionCodes[0])
            : this.permissionService.hasAnyPermission(this.permissionCodes);
        const hasPermission = hasCode && (!this.adminTools || this.authService.isMinistryAdminOrAbove());

        if (hasPermission && !this.isShown) {
            this.viewContainer.createEmbeddedView(this.templateRef);
            this.isShown = true;
        } else if (!hasPermission && this.isShown) {
            this.viewContainer.clear();
            this.isShown = false;
        }
    }
}
