import { Component } from '@angular/core';

/**
 * The portal's own "no such page". The catch-all used to redirect to the
 * dashboard, so a mistyped or stale link silently landed the student somewhere
 * else with no word that the page they asked for does not exist.
 *
 * Rendered inside the portal layout, so it is guarded like every other portal
 * page: without a session token StudentPortalGuard sends the student to login.
 */
@Component({
    selector: 'app-portal-not-found',
    templateUrl: './portal-not-found.component.html',
    styleUrls: ['./portal-not-found.component.scss'],
    standalone: false
})
export class PortalNotFoundComponent {}
