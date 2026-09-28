import { Component, NO_ERRORS_SCHEMA } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { RouterModule } from '@angular/router';
import { of } from 'rxjs';
import { ConfirmationService, MessageService } from 'primeng/api';

import { StudentsModule } from './students.module';
import { StudentsComponent } from './students.component';
import { StudentdataService } from '../../service/studentdata.service';
import { StudentdataAddService } from '../../service/studentdata-add.service';
import { studentCountryService } from '../../service/studentcountry.service';
import { StudentDocumentService } from '../../service/student-document.service';
import { LookupService } from '../../service/lookup.service';
import { TranslationService } from '../../service/translation.service';
import { ApprovalService } from '../../service/approval.service';

/**
 * Which way the students table reads.
 *
 * <p>Reported in English: the columns ran the wrong way — Actions at the left
 * edge, the select-all checkbox at the right, the sort arrows on the wrong side
 * of their labels — while the page around the table was correctly
 * left-to-right. The table is the only thing in the application that is given
 * `direction` explicitly rather than inheriting it (`styles.scss` sets
 * `[dir="rtl"] .p-datatable table { direction: rtl }` so the columns mirror),
 * which is what makes it worth measuring on its own.
 *
 * <p>Measured by position rather than by reading the `dir` attribute back: the
 * attribute was right in the case that was wrong.
 */

const quiet = () => new Proxy({} as any, { get: () => () => of([]) });
const page = () => of({ content: [], totalElements: 0 });

let rtl = false;
const translation = {
    currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
    isRTL: () => rtl, translate: (k: string) => k, getCurrentLanguage: () => (rtl ? 'ar' : 'en')
};

describe('the students table direction', () => {

    let fixture: any;
    let host: HTMLElement;

    const build = async (dir: 'ltr' | 'rtl') => {
        rtl = dir === 'rtl';

        TestBed.resetTestingModule();
        await TestBed.configureTestingModule({
            imports: [StudentsModule, RouterModule.forRoot([])],
            providers: [
                MessageService,
                ConfirmationService,
                { provide: HttpClient, useValue: { get: () => page(), post: () => of({}), put: () => of({}) } },
                { provide: StudentdataService, useValue: { getPagingAll: () => page(), getAll: () => of([]), getPendingStudentIds: () => of([]) } },
                { provide: StudentdataAddService, useValue: quiet() },
                { provide: studentCountryService, useValue: quiet() },
                { provide: StudentDocumentService, useValue: quiet() },
                { provide: LookupService, useValue: quiet() },
                { provide: ApprovalService, useValue: quiet() },
                { provide: TranslationService, useValue: translation }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(StudentsComponent);
        fixture.detectChanges();
        await fixture.whenStable();

        // The shell marks the direction two ways and the stylesheets key off
        // both; a host that set only one would miss half the rules.
        host = document.createElement('div');
        host.setAttribute('dir', dir);
        if (rtl) { host.className = 'rtl'; }
        document.documentElement.classList.toggle('rtl', rtl);
        document.body.setAttribute('dir', dir);
        document.body.appendChild(host);
        host.appendChild(fixture.nativeElement);

        fixture.detectChanges();
    };

    afterEach(() => {
        host?.remove();
        document.documentElement.classList.remove('rtl');
        document.body.setAttribute('dir', 'ltr');
        rtl = false;
    });

    /** The first and last header cells of the main table. */
    const ends = () => {
        const ths = Array.from(
            fixture.nativeElement.querySelectorAll('table thead tr th')
        ) as HTMLElement[];
        return { first: ths[0].getBoundingClientRect(), last: ths[ths.length - 1].getBoundingClientRect() };
    };

    it('runs left to right in English', async () => {
        await build('ltr');
        const { first, last } = ends();
        expect(first.left)
            .withContext('the first column is not at the left; the table is reading right to left')
            .toBeLessThan(last.left);
    });

    it('runs right to left in Arabic', async () => {
        await build('rtl');
        const { first, last } = ends();
        expect(first.left)
            .withContext('the first column is not at the right; the table is not mirrored')
            .toBeGreaterThan(last.left);
    });

    /**
     * The reported case: Arabic first, then English. The page is rebuilt on a
     * switch (AppLayoutComponent.reinitialiseRoute), but the direction marks
     * move on the shell that survives it, so this turns them over on the
     * standing component rather than building a new one — if the table only
     * reads correctly when it is built from scratch, that is worth knowing.
     */
    it('turns back around when the language does', async () => {
        await build('rtl');

        rtl = false;
        host.setAttribute('dir', 'ltr');
        host.classList.remove('rtl');
        document.documentElement.classList.remove('rtl');
        document.body.setAttribute('dir', 'ltr');
        fixture.detectChanges();

        const { first, last } = ends();
        expect(first.left)
            .withContext('the table stayed right-to-left after the switch to English')
            .toBeLessThan(last.left);
    });
});

/**
 * The global rule that mirrors a table's columns, on its own.
 *
 * <p>`styles.scss` gives the table an explicit direction rather than letting it
 * inherit one, because the columns have to mirror:
 *
 * <pre>[dir="rtl"] .p-datatable table { direction: rtl; }</pre>
 *
 * <p>That is keyed on an *ancestor* and has no counterpart for the other
 * direction, so a table that says `dir="ltr"` — every `p-table` in this
 * application carries the binding — is still turned around by any outer element
 * that says rtl, and nothing puts it back. The pages set the attribute on the
 * table for a reason; it should be what decides.
 */
@Component({
    template: `
    <div [attr.dir]="outer">
      <p-table-stub class="p-datatable" [attr.dir]="inner">
        <table><thead><tr><th>first</th><th>last</th></tr></thead></table>
      </p-table-stub>
    </div>`,
    standalone: false
})
class TableDirectionHost {
    outer = 'ltr';
    inner = 'ltr';
}

describe('a table follows the direction it was given', () => {

    let fixture: any;

    beforeEach(async () => {
        await TestBed.resetTestingModule().configureTestingModule({
            declarations: [TableDirectionHost],
            schemas: [NO_ERRORS_SCHEMA]
        }).compileComponents();
        fixture = TestBed.createComponent(TableDirectionHost);
        document.body.appendChild(fixture.nativeElement);
    });

    afterEach(() => fixture.nativeElement.remove());

    const set = (outer: string, inner: string) => {
        fixture.componentInstance.outer = outer;
        fixture.componentInstance.inner = inner;
        fixture.detectChanges();
    };

    const direction = () =>
        getComputedStyle(fixture.nativeElement.querySelector('table')).direction;

    it('mirrors when it says rtl', () => {
        set('rtl', 'rtl');
        expect(direction()).toBe('rtl');
    });

    it('does not mirror when it says ltr, whatever is outside it', () => {
        set('rtl', 'ltr');
        expect(direction())
            .withContext('an outer element turned a table that says it is ltr')
            .toBe('ltr');
    });
});
