import { Injectable } from '@angular/core';
import { PreloadingStrategy, Route } from '@angular/router';
import { EMPTY, Observable, timer } from 'rxjs';
import { mergeMap } from 'rxjs/operators';
import { AuthService } from './demo/service/auth.service';

/** How long after a navigation the first chunk is fetched, and the gap between chunks. */
const START_DELAY_MS = 2500;
const STAGGER_MS = 400;

/**
 * Fetches the lazy feature modules in the background once the user is signed in
 * (review round 2, H3 — "moving between pages is slow").
 *
 * <p>Every feature route is lazy, so the first visit to a section waited for its
 * chunk before the guard, the page and its API calls could even start. This
 * loads them ahead of time, but not the way {@code PreloadAllModules} does:
 *
 * <ul>
 *   <li>not before sign-in — the router asks again after every navigation, so
 *       the login page downloads nothing and the first page after it does;</li>
 *   <li>not while the first page is still loading its own data — the first chunk
 *       waits {@link START_DELAY_MS}, and the rest follow one every
 *       {@link STAGGER_MS} instead of some thirty requests at once;</li>
 *   <li>not on a connection that asked to save data;</li>
 *   <li>not a route marked {@code data: { preload: false } } (the student
 *       portal, which staff never open).</li>
 * </ul>
 *
 * Preloading only downloads code: guards still run when the route is visited.
 */
@Injectable({ providedIn: 'root' })
export class DelayedPreloadingStrategy implements PreloadingStrategy {

    private queued = 0;

    constructor(private authService: AuthService) { }

    preload(route: Route, load: () => Observable<any>): Observable<any> {
        if (route.data?.['preload'] === false || !this.authService.isLoggedIn() || this.saveData()) {
            return EMPTY;
        }
        const delay = START_DELAY_MS + STAGGER_MS * this.queued++;
        return timer(delay).pipe(mergeMap(() => {
            this.queued = Math.max(0, this.queued - 1);
            return load();
        }));
    }

    private saveData(): boolean {
        const connection = (navigator as Navigator & { connection?: { saveData?: boolean } }).connection;
        return connection?.saveData === true;
    }
}
