import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { RouterModule } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MessageService, ConfirmationService } from 'primeng/api';
import { of } from 'rxjs';

import { StudentExtensionModule } from './student-extension/student-extension.module';
import { StudentExtensionComponent } from './student-extension/student-extension.component';
import { StudentTravelTicketsModule } from './student-travel-tickets/student-travel-tickets.module';
import { StudentTravelTicketsComponent } from './student-travel-tickets/student-travel-tickets.component';

import { StudentExtensionService } from '../service/student-extension.service';
import { StudentTravelTicketService } from '../service/student-travel-ticket.service';
import { StudentdataService } from '../service/studentdata.service';
import { LookupService } from '../service/lookup.service';
import { TranslationService } from '../service/translation.service';

/**
 * The icon in front of the heading on Student extension and Travel tickets.
 *
 * <p>Of the six pages the owner named, four already led with one — students has
 * a header band with `pi-users` in it, quick edit a hero with `pi-pencil`,
 * student history `pi-users` beside the title, and MOHE quarter `pi-calendar` in
 * a tinted badge. These two did not: both are `p-card`s whose header template is
 * a `.card-heading` holding an `h2` and a `p`, and nothing else.
 *
 * <p>They are not in `page-headings.spec.ts`, which measures a `.card` holding an
 * `h1..h5` in a flex row — a `p-card` has neither, so that spec finds nothing on
 * either page and every one of its checks throws rather than fails. Bringing the
 * two under it is a larger job than adding an icon, and is not this.
 *
 * <p>Rendered rather than read off the template: what is being asserted is that
 * the icon is in the header, before the words, and that the two are on one line —
 * an icon that renders under the title is not in front of it.
 */

const quiet = () => new Proxy({} as any, { get: () => () => of([]) });
const page = () => of(Object.assign([] as any[], { content: [], totalElements: 0, totalPages: 0 }));

const translation = {
    currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
    isRTL: () => false, getCurrentLanguage: () => 'en',
    translate: (key: string) => key.split('.').pop()!.replace(/_/g, ' ')
};

const build = async (module: any, component: any): Promise<any> => {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
        imports: [module, RouterModule.forRoot([])],
        providers: [
            provideNoopAnimations(),
            MessageService,
            ConfirmationService,
            { provide: HttpClient, useValue: { get: () => page(), post: () => of({}), put: () => of({}), delete: () => of({}) } },
            { provide: StudentExtensionService, useValue: new Proxy({} as any, { get: () => () => page() }) },
            { provide: StudentTravelTicketService, useValue: new Proxy({} as any, { get: () => () => page() }) },
            { provide: StudentdataService, useValue: { getPagingAll: () => page(), getAll: () => of([]), getPendingStudentIds: () => of([]) } },
            { provide: LookupService, useValue: quiet() },
            { provide: TranslationService, useValue: translation }
        ]
    }).compileComponents();

    const fixture = TestBed.createComponent(component);
    const host = document.createElement('div');
    document.body.appendChild(host);
    host.appendChild(fixture.nativeElement);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, host };
};

([['student extension', StudentExtensionModule, StudentExtensionComponent],
  ['student travel tickets', StudentTravelTicketsModule, StudentTravelTicketsComponent]] as Array<[string, any, any]>)
    .forEach(([name, module, component]) => {

        describe(`${name} heading`, () => {

            let fixture: any;
            let host: HTMLElement;

            beforeEach(async () => ({ fixture, host } = await build(module, component)));
            afterEach(() => host.remove());

            const heading = () => fixture.nativeElement.querySelector('.card-heading h2') as HTMLElement;
            const icon = () => fixture.nativeElement.querySelector('.card-heading > i[class*="pi-"]') as HTMLElement;

            it('has an icon in its heading block', () => {
                expect(heading()).withContext('no heading to lead').toBeTruthy();
                expect(icon())
                    .withContext(`${name} still opens with the words alone`)
                    .toBeTruthy();
            });

            it('puts it in front of the words rather than above them', () => {
                // A `<i>` before the title in the DOM but laid out on its own line
                // is not in front of anything. Compared by vertical position,
                // which holds in both directions — unlike a left/right test.
                const i = icon().getBoundingClientRect();
                const h = heading().getBoundingClientRect();

                expect(i.bottom).withContext('the icon sits above the title').toBeGreaterThan(h.top);
                expect(i.top).withContext('the icon sits below the title').toBeLessThan(h.bottom);
            });

            it('sizes it as a heading icon rather than as body text', () => {
                // 2.5rem is what the pages fixed before this use; the check is
                // that it is not simply inheriting the paragraph size around it.
                const size = parseFloat(getComputedStyle(icon()).fontSize);
                const body = parseFloat(getComputedStyle(document.body).fontSize);
                expect(size)
                    .withContext(`the icon renders at ${size}px against ${body}px of body text`)
                    .toBeGreaterThan(body * 1.5);
            });
        });
    });
