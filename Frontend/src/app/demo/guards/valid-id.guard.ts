import { inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateFn, Router } from '@angular/router';

/** Whether a route id is a positive whole number, which every numeric key in the system is. */
export function isPositiveIntId(raw: string | null | undefined): boolean {
    return !!raw && /^[1-9]\d{0,9}$/.test(raw);
}

/**
 * Sends a route with a malformed id (`abc`, `1.5`, `0`, `-3`) to the not-found page.
 *
 * <p>Without it `#/student-add/edit/abc` opened an empty, editable wizard,
 * `#/finance/edit/abc` showed two error toasts and then "record not found", and
 * `#/students/profile/1.5` reached the backend and printed its type error (QA STD-003).
 *
 * <p>The parameter is `id` unless the route names another in `data.idParam`.
 */
export const validIdGuard: CanActivateFn = (route: ActivatedRouteSnapshot) => {
    const name = (route.data?.['idParam'] as string) || 'id';
    return isPositiveIntId(route.paramMap.get(name)) ? true : inject(Router).createUrlTree(['/notfound']);
};
