import { ApplicationRef, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { BidiModule } from '@angular/cdk/bidi';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzTableModule } from 'ng-zorro-antd/table';
import { NzModalModule, NzModalService } from 'ng-zorro-antd/modal';
import { NzConfigService } from 'ng-zorro-antd/core/config';

import { TranslationService } from '../demo/service/translation.service';

/**
 * ng-zorro follows a language switch — live, without a reload.
 *
 * <p>Every ng-zorro widget takes its direction from the CDK `Directionality`.
 * There are two of those. The root one, which any widget gets by default,
 * reads `document.body.dir` once when it is first injected and never emits a
 * change. The other is provided by the `Dir` directive (`[dir]` on an element
 * with BidiModule loaded), which re-reads its input and does emit. The layout
 * wrapper used to carry a bare `[attr.dir]`, so every card, table and input
 * under it asked the root — read at bootstrap, before the user had switched
 * anything — and kept its English layout until the next reload. A card's
 * `nzExtra` (the Add button) is the visible case: `ant-card-rtl` is what moves
 * it to the left, and it was never put on.
 *
 * <p>Modals are opened by `NzModalService`, a root service that asks the root
 * `Directionality` too, unless the modal config says otherwise. So the wrapper
 * directive fixes cards and tables and inputs, and `TranslationService` sets
 * the modal config on every switch. Both halves are measured here, and the
 * bare-attribute host is kept as the control that shows what the directive is
 * doing.
 */

// The same card and table twice over, written out rather than interpolated:
// the template linter reads an inline template as a whole and cannot see
// past a `${}`.
@Component({
    template: `<div [dir]="dir">
  <nz-card nzTitle="Title" [nzExtra]="extra">
    <ng-template #extra><button type="button">Add</button></ng-template>
    body
  </nz-card>
  <nz-table [nzData]="[]" [nzShowPagination]="false">
    <thead><tr><th>h</th></tr></thead>
  </nz-table>
</div>`,
    standalone: false
})
class DirectiveHost { dir: 'ltr' | 'rtl' = 'ltr'; }

@Component({
    template: `<div [attr.dir]="dir">
  <nz-card nzTitle="Title" [nzExtra]="extra">
    <ng-template #extra><button type="button">Add</button></ng-template>
    body
  </nz-card>
  <nz-table [nzData]="[]" [nzShowPagination]="false">
    <thead><tr><th>h</th></tr></thead>
  </nz-table>
</div>`,
    standalone: false
})
class AttributeHost { dir: 'ltr' | 'rtl' = 'ltr'; }

describe('live direction for ng-zorro', () => {

    let fixture: any;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [DirectiveHost, AttributeHost],
            imports: [BidiModule, NzCardModule, NzTableModule],
            providers: [provideNoopAnimations()]
        }).compileComponents();
    });

    afterEach(() => fixture?.nativeElement.remove());

    const build = (host: any) => {
        fixture = TestBed.createComponent(host);
        // Attached, or nothing has a layout and the extra has no side to be on.
        document.body.appendChild(fixture.nativeElement);
        fixture.detectChanges();
        return fixture.componentInstance;
    };

    const has = (cls: string) => !!fixture.nativeElement.querySelector(`.${cls}`);
    const rect = (sel: string) => fixture.nativeElement.querySelector(sel).getBoundingClientRect();

    /** The title's glyphs: its box is flex-1 and meets the extra edge to edge. */
    const titleText = () => {
        const range = document.createRange();
        range.selectNodeContents(fixture.nativeElement.querySelector('.ant-card-head-title'));
        return range.getBoundingClientRect();
    };

    it('mirrors a card and a table when the wrapper direction flips', () => {
        const host = build(DirectiveHost);
        expect(has('ant-card-rtl')).withContext('rtl before any switch').toBeFalse();

        host.dir = 'rtl';
        fixture.detectChanges();
        expect(has('ant-card-rtl')).withContext('card after the switch').toBeTrue();
        expect(has('ant-table-wrapper-rtl') || has('ant-table-rtl'))
            .withContext('table after the switch').toBeTrue();

        host.dir = 'ltr';
        fixture.detectChanges();
        expect(has('ant-card-rtl')).withContext('card after switching back').toBeFalse();
    });

    it('puts the card extra at the far end in both directions', () => {
        const host = build(DirectiveHost);
        expect(rect('.ant-card-extra button').left).toBeGreaterThan(titleText().right);

        host.dir = 'rtl';
        fixture.detectChanges();
        expect(rect('.ant-card-extra button').right).toBeLessThan(titleText().left);
    });

    it('does nothing for a bare attribute — the fault this guards against', () => {
        // The root Directionality answered whatever body.dir was when it was
        // first asked; flipping the attribute afterwards changes nothing it
        // sees. Compared before and after rather than against a constant, so
        // the check holds whatever an earlier spec left on <body>.
        const host = build(AttributeHost);
        const before = has('ant-card-rtl');

        host.dir = 'rtl';
        fixture.detectChanges();
        expect(has('ant-card-rtl')).toBe(before);
    });
});

describe('live direction for ng-zorro modals', () => {

    let modal: NzModalService;
    let config: NzConfigService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [NzModalModule],
            providers: [provideNoopAnimations()]
        }).compileComponents();
        modal = TestBed.inject(NzModalService);
        config = TestBed.inject(NzConfigService);
    });

    afterEach(() => {
        modal.closeAll();
        config.set('modal', { nzDirection: undefined });
    });

    const wrap = () => document.querySelector('.ant-modal-wrap') as HTMLElement;

    it('opens in the direction the config holds', () => {
        config.set('modal', { nzDirection: 'rtl' });
        modal.create({ nzContent: 'x', nzFooter: null });
        TestBed.inject(ApplicationRef).tick();

        expect(wrap()).withContext('no modal container').toBeTruthy();
        expect(wrap().classList.contains('ant-modal-wrap-rtl')).toBeTrue();
    });
});

describe('TranslationService and the modal direction', () => {

    let service: TranslationService;
    let config: NzConfigService;
    let stored: string | null;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: HttpClient, useValue: { get: () => of({}) } }]
        });
        service = TestBed.inject(TranslationService);
        config = TestBed.inject(NzConfigService);
        try { stored = localStorage.getItem('selectedLanguage'); } catch { stored = null; }
    });

    afterEach(() => {
        // setLanguage writes <body dir>, <html class="rtl"> and localStorage;
        // none of that may leak into the next spec.
        service.setLanguage('en');
        try {
            if (stored === null) { localStorage.removeItem('selectedLanguage'); }
            else { localStorage.setItem('selectedLanguage', stored); }
        } catch { /* storage unavailable; nothing was written either */ }
    });

    it('tells the modal config the direction on every switch', () => {
        service.setLanguage('ar');
        expect(config.getConfigForComponent('modal')?.nzDirection).toBe('rtl');

        service.setLanguage('en');
        expect(config.getConfigForComponent('modal')?.nzDirection).toBe('ltr');
    });
});
