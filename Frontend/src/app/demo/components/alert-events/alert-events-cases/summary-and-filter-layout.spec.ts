import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterModule } from '@angular/router';
import { of } from 'rxjs';
import { MessageService } from 'primeng/api';
import { AlertEventsCasesComponent } from './alert-events-cases.component';
import { AlertEventsModule } from '../alert-events.module';
import { AlertEventsService } from '../../../service/alert-events.service';
import { LookupService } from '../../../service/lookup.service';
import { TranslationService } from '../../../service/translation.service';
import { UmsPermissionService } from '../../../service/ums-permission.service';
import { CodeListService } from '../../../service/code-list.service';
import { codeListStub } from '../../../../testing/code-list.stub';

/**
 * The summary band and the filter row on the alert cases page.
 *
 * <p>The four counts were `card border-left-3 text-center` blocks on primeflex
 * columns — a fifth private spelling of a band that four other pages also had.
 * The filters were a primeflex grid whose last column held only the two
 * buttons, bottom-aligned with `align-items-end` against invisible label
 * height.
 *
 * <p>Both layouts are covered centrally, in src/app/summary-cards-shared.spec.ts
 * and src/app/filter-bar-shared.spec.ts. What is pinned here belongs to this
 * page: that it uses both blocks, that the summary starts collapsed, and that
 * the severity chips survived the move — they hang off the open count alone,
 * because how urgent the backlog is only means anything for open cases.
 */
describe('AlertEventsCases summary and filter', () => {

    let fixture: ComponentFixture<AlertEventsCasesComponent>;
    let component: AlertEventsCasesComponent;

    const severity = (code: string, count: number, uiSeverity: string, sortOrder: number) =>
        ({ code, nameAr: `${code}-ar`, nameEn: `${code}-en`, uiSeverity, icon: null, sortOrder, count });
    const summary = {
        totalOpen: 5, totalAcknowledged: 0, totalResolved: 1, totalDismissed: 0, totalAll: 6,
        openCritical: 2, openHigh: 0, openMedium: 3, openLow: 0,
        // The legend rows the server sends: two of the four are non-zero.
        bySeverity: [severity('CRITICAL', 2, 'danger', 1), severity('HIGH', 0, 'warn', 2), severity('MEDIUM', 3, 'info', 3), severity('LOW', 0, 'success', 4)],
        byStatus: []
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [AlertEventsModule, RouterModule.forRoot([])],
            providers: [
                MessageService,
                { provide: CodeListService, useValue: codeListStub() },
                {
                    provide: AlertEventsService,
                    useValue: new Proxy({} as any, {
                        get: (_t, prop) => {
                            if (prop === 'getSummary') { return () => of(summary); }
                            if (prop === 'getCases') {
                                return () => of({ content: [], totalElements: 0 });
                            }
                            return () => of([]);
                        }
                    })
                },
                {
                    provide: LookupService,
                    useValue: new Proxy({} as any, { get: () => () => of([]) })
                },
                {
                    provide: UmsPermissionService,
                    useValue: { hasPermission: () => true, getAllowedCountryIds: () => [] }
                },
                {
                    provide: TranslationService,
                    useValue: {
                        currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
                        isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en'
                    }
                }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(AlertEventsCasesComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
        await fixture.whenStable();

        component.summary = { ...summary } as any;
        fixture.detectChanges();

        // Attached to the document, or nothing has a computed layout at all.
        document.body.appendChild(fixture.nativeElement);
        fixture.detectChanges();
    });

    afterEach(() => fixture.nativeElement.remove());

    const el = (selector: string) => fixture.nativeElement.querySelector(selector) as HTMLElement;
    const all = (selector: string) =>
        Array.from(fixture.nativeElement.querySelectorAll(selector)) as HTMLElement[];

    /**
     * Opens the summary section.
     *
     * p-panel is OnPush, so assigning `collapsed` imperatively never marks it
     * dirty. Clicking the toggler is what a user does and what schedules the
     * check; the expand is animated, so the animations are finished before
     * anything is measured — collapsed content has no layout box at all.
     */
    function openSummary(): void {
        (fixture.nativeElement.querySelector('.p-panel-header button') as HTMLElement).click();
        fixture.detectChanges();

        (fixture.nativeElement as HTMLElement)
            .getAnimations({ subtree: true })
            .forEach(animation => animation.finish());
        fixture.detectChanges();
    }

    // ───────────────────────────────────────────────────────────── summary ──

    it('keeps the counts behind a collapsed section until asked', () => {
        const region = fixture.nativeElement.querySelector('[role="region"]') as HTMLElement;
        expect(region.getAttribute('aria-hidden'))
            .withContext('the section should start closed').toBe('true');

        const cards = all('.summary-card');
        expect(cards.length).toBe(4);
        expect(cards[0].getBoundingClientRect().height).toBe(0);

        openSummary();

        expect(region.getAttribute('aria-hidden')).toBe('false');
        expect(all('.summary-card')[0].getBoundingClientRect().height).toBeGreaterThan(0);
    });

    it('uses the shared summary band rather than primeflex columns', () => {
        openSummary();

        const grid = el('.summary-cards');
        expect(grid.classList).toContain('summary-cards--cols-4');
        expect(grid.querySelector('[class*="col-"]'))
            .withContext('the col-6/md:col-3 wrappers should be gone').toBeNull();
    });

    it('gives each count its own accent', () => {
        openSummary();

        const accents = all('.summary-card').map(card =>
            getComputedStyle(card).getPropertyValue('--summary-accent').trim());

        expect(accents.every(a => a !== '')).withContext(`accents: ${accents}`).toBeTrue();
        expect(new Set(accents).size).toBe(4);
    });

    it('hangs the severity chips off the open count only', () => {
        openSummary();

        // Two of the four severities are non-zero in the fixture, and the split
        // says how urgent the backlog is rather than how big — which means
        // nothing for cases already acknowledged or resolved.
        const open = el('.summary-card.open');
        expect(open.querySelectorAll('p-tag').length).toBe(2);

        all('.summary-card').filter(c => c !== open).forEach(card => {
            expect(card.querySelectorAll('p-tag').length)
                .withContext(`${card.className} should carry no chips`).toBe(0);
        });
    });

    it('lays the chips out in a row rather than one per line', () => {
        openSummary();

        // .summary-meta is a block, so the chips needed a rule of their own.
        const chips = Array.from(el('.summary-severities').querySelectorAll('p-tag'))
            .map(t => Math.round((t as HTMLElement).getBoundingClientRect().top));

        expect(new Set(chips).size).withContext(`chip tops: ${chips}`).toBe(1);
    });

    // ────────────────────────────────────────────────────────────── filters ──

    it('uses the shared filter bar', () => {
        expect(el('.filter-bar')).toBeTruthy();
        expect(all('.filter-bar__field').length).toBe(4);
    });

    it('gives the two sentence-length fields the wider columns', () => {
        // Scenario names and student names are sentences; severity and status
        // are single words.
        const wide = all('.filter-bar__field--wide')
            .map(f => f.querySelector('label')?.getAttribute('for'));

        expect(wide).toEqual(['aeScenario', 'aeStudent']);
    });

    it('keeps the buttons out of the field grid', () => {
        const actions = el('.filter-bar__actions');

        expect(actions.querySelectorAll('p-button').length).toBe(2);
        expect(el('.filter-bar__grid').contains(actions)).toBeFalse();
    });

    it('gives every filter a label bound to its control', () => {
        all('.filter-bar__field').forEach((field, i) => {
            const label = field.querySelector('label') as HTMLLabelElement;
            expect(label).withContext(`field ${i} has no label`).toBeTruthy();
            expect(label.htmlFor).withContext(`field ${i} label is not bound`).not.toBe('');
        });
    });

    it('marks only the filters that are set', () => {
        const active = () => all('.filter-bar__field--active').length;

        expect(active()).toBe(0);

        component.filterSeverity = 'CRITICAL';
        component.filterStudentName = 'ahmed';
        fixture.detectChanges();

        expect(active()).toBe(2);
    });

    it('says the figures could not be loaded instead of showing zeros (QA B9)', () => {
        component.summaryFailed = true;
        fixture.detectChanges();
        const header = el('.p-panel-header') ?? fixture.nativeElement;

        expect(component.summaryFailed).toBeTrue();
        expect(header.querySelector('p-tag')).withContext('the total still showed').toBeNull();
    });

    it('goes back to the first page when a filter changes, the table included (QA B8)', () => {
        component.onPage({ first: 40, rows: 20 });
        expect(component.page).toBe(2);
        expect(component.first).toBe(40);

        component.applyFilters();

        expect(component.page).toBe(0);
        expect(component.first).withContext('the paginator stayed on page 3').toBe(0);
    });
});
