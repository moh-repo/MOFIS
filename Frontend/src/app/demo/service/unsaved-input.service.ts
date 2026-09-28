import { Injectable } from '@angular/core';

/**
 * Whether the page on screen holds input the user typed and has not saved.
 *
 * <p>A language switch rebuilds the routed page (AppLayoutComponent.reinitialiseRoute),
 * and a country switch re-navigates to it, so anything typed into a form was wiped
 * without a word — the Add Student wizard, a filter, a half-written reason (live
 * QA #4, N15). Decision DEC-07 (a): ask first.
 *
 * <p>Angular marks every control the user has changed with `ng-dirty`, whichever
 * form API the page uses (template-driven `ngModel`, reactive forms, PrimeNG and
 * ng-zorro inputs), so looking for one inside the page area answers the question
 * for every page without each of them registering anything.
 */
@Injectable({ providedIn: 'root' })
export class UnsavedInputService {

    /** The staff shell's page area, and the portal's. */
    static readonly STAFF_PAGE = '.layout-main';
    static readonly PORTAL_PAGE = '.portal-content-inner';

    hasUnsavedInput(pageSelector: string = UnsavedInputService.STAFF_PAGE,
                    root: ParentNode = document): boolean {
        const page = root.querySelector(pageSelector);
        return !!page?.querySelector('.ng-dirty');
    }
}
