import { Directive, Input, TemplateRef, ViewContainerRef, OnInit, OnDestroy } from '@angular/core';
import { UmsPermissionService } from '../service/ums-permission.service';
import { UmsAuthService } from '../service/ums-auth.service';
import { Subscription } from 'rxjs';

/**
 * Structural directive that conditionally includes a template based on permission.
 *
 * Usage:
 *   *hasPermission="'STUDENTS.EDIT'"                      — one code
 *   *hasPermission="'STUDENTS|EDIT'"                      — same, legacy separator
 *   *hasPermission="['DASHBOARD.VIEW', 'DASHBOARD_FINANCE.VIEW']" — any-of: shown when the
 *                                                           user holds at least one of them
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

    ngOnInit(): void {
        // Subscribe to permission changes to reactively update the view
        this.permissionSubscription = this.authService.permissions$.subscribe(() => {
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
        const hasPermission = this.permissionCodes.length === 1
            ? this.permissionService.hasPermission(this.permissionCodes[0])
            : this.permissionService.hasAnyPermission(this.permissionCodes);

        if (hasPermission && !this.isShown) {
            this.viewContainer.createEmbeddedView(this.templateRef);
            this.isShown = true;
        } else if (!hasPermission && this.isShown) {
            this.viewContainer.clear();
            this.isShown = false;
        }
    }
}
