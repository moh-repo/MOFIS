import { Component, NO_ERRORS_SCHEMA } from '@angular/core';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { Router, RouterModule } from '@angular/router';
import { of } from 'rxjs';

import { AppLayoutComponent } from './app.layout.component';
import { MenuService } from './app.menu.service';
import { TranslationService } from '../demo/service/translation.service';
import { AuthService } from '../demo/service/auth.service';
import { UmsAuthService } from '../demo/service/ums-auth.service';
import { IdleTimeoutService } from '../demo/service/idle-timeout.service';
import { AlertStreamService } from '../demo/service/alert-stream.service';

/**
 * A language switch builds the routed page again.
 *
 * <p>Switching the language used to reload the whole application, which is why
 * every screen looked right afterwards; the reload went in the third pass, on
 * the grounds that no consumer needed it. Three kinds of consumer did, and none
 * of them can be reached by change detection: text the *server* chose for the
 * language the request was made in (the backend reads `LocaleContextHolder` to
 * pick `nameEn` or `nameAr`, so a chart's labels are whatever they were fetched
 * in), strings a component built once into a field in `ngOnInit`, and
 * third-party widgets that read the direction when they are created. Users were
 * pressing F5 by hand to get what the application used to do for them.
 *
 * <p>What is asserted here is that the page is destroyed and built again — a
 * new component instance, which means a second `ngOnInit`, which is what
 * re-fetches and rebuilds. And that the shell around it is *not*, because the
 * point of doing this at the outlet rather than at the window is that the
 * session, the menu and the country selection stay up.
 */

@Component({ template: 'routed page', standalone: false })
class RoutedProbe {
    static built = 0;
    constructor() { RoutedProbe.built++; }
}

/** The real application routes every page lazily, so the outlet has to
 *  re-activate one of those, not just an eagerly declared component. */
@Component({ template: 'lazy page', standalone: true })
class LazyProbe {
    static built = 0;
    constructor() { LazyProbe.built++; }
}

const quiet = () => new Proxy({} as any, { get: () => () => of([]) });

describe('a language switch and the routed page', () => {

    let fixture: any;
    let translation: TranslationService;
    let stored: string | null;

    beforeEach(async () => {
        RoutedProbe.built = 0;

        await TestBed.configureTestingModule({
            declarations: [AppLayoutComponent, RoutedProbe],
            imports: [RouterModule.forRoot([{ path: '**', component: RoutedProbe }])],
            // The shell's own children — topbar, sidebar, footer, config, the pdf
            // modal — are not what this measures, and each drags in its own half
            // of the application.
            schemas: [NO_ERRORS_SCHEMA],
            providers: [
                { provide: HttpClient, useValue: { get: () => of({}) } },
                { provide: MenuService, useValue: { hasVisibleItems$: of(true), reset: () => undefined } },
                { provide: AuthService, useValue: { currentUserValue: null } },
                { provide: UmsAuthService, useValue: { permissions: ['X'], loadPermissionsForUser: () => of([]) } },
                { provide: IdleTimeoutService, useValue: { start: () => undefined, stop: () => undefined } },
                { provide: AlertStreamService, useValue: quiet() }
            ]
        }).compileComponents();

        translation = TestBed.inject(TranslationService);
        try { stored = localStorage.getItem('selectedLanguage'); } catch { stored = null; }
    });

    afterEach(() => {
        fixture?.destroy();
        translation.setLanguage('en');
        try {
            if (stored === null) { localStorage.removeItem('selectedLanguage'); }
            else { localStorage.setItem('selectedLanguage', stored); }
        } catch { /* storage unavailable; nothing was written either */ }
    });

    /** Builds the shell and lets the router put the probe in its outlet. Must be
     *  called from inside a fakeAsync test: it ticks the initial navigation. */
    const build = () => {
        fixture = TestBed.createComponent(AppLayoutComponent);
        fixture.detectChanges();
        TestBed.inject(Router).initialNavigation();
        tick();
        fixture.detectChanges();
    };

    it('builds the page once to begin with', fakeAsync(() => {
        build();
        expect(RoutedProbe.built).toBe(1);
    }));

    it('builds it again when the language changes', fakeAsync(() => {
        build();

        translation.setLanguage('ar');
        fixture.detectChanges();
        tick();
        fixture.detectChanges();

        expect(RoutedProbe.built)
            .withContext('the page was not rebuilt, so anything the server named stays in the old language')
            .toBe(2);
    }));

    it('builds it again on the way back too', fakeAsync(() => {
        build();

        translation.setLanguage('ar');
        fixture.detectChanges();
        tick();
        translation.setLanguage('en');
        fixture.detectChanges();
        tick();
        fixture.detectChanges();

        expect(RoutedProbe.built).toBe(3);
    }));

    it('does not rebuild it when the language is set to the one already showing', fakeAsync(() => {
        build();

        // switchLanguage is what the topbar calls, and it returns early rather
        // than emitting when nothing changed.
        translation.switchLanguage('en');
        fixture.detectChanges();
        tick();
        fixture.detectChanges();

        expect(RoutedProbe.built).toBe(1);
    }));

    it('builds a lazily loaded page again too', fakeAsync(() => {
        build();
        // Every route in the application is loadChildren/loadComponent. Once the
        // chunk is loaded the outlet re-activates from the route that is already
        // active, so no second load happens — but the component is built again,
        // which is the part that matters.
        TestBed.inject(Router).resetConfig([
            { path: '**', loadComponent: () => Promise.resolve(LazyProbe) }
        ]);
        LazyProbe.built = 0;
        TestBed.inject(Router).navigateByUrl('/lazy');
        tick();
        fixture.detectChanges();
        expect(LazyProbe.built).withContext('lazy page did not load').toBe(1);

        translation.setLanguage('ar');
        fixture.detectChanges();
        tick();
        fixture.detectChanges();

        expect(LazyProbe.built).toBe(2);
    }));

    it('leaves the shell itself alone', fakeAsync(() => {
        build();
        const shell = fixture.componentInstance;

        translation.setLanguage('ar');
        fixture.detectChanges();
        tick();
        fixture.detectChanges();

        // Same shell instance: the session, the menu and the country selection
        // live above the outlet and are not rebuilt with the page.
        expect(fixture.componentInstance).toBe(shell);
        expect(shell.routeAlive).withContext('the outlet was left switched off').toBeTrue();
    }));
});
