import { Component, ElementRef, ViewChild } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { DatePickerModule } from 'primeng/datepicker';
import { Popover, PopoverModule } from 'primeng/popover';
import { PrimeNG, providePrimeNG } from 'primeng/config';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { definePreset } from '@primeng/themes';
import Aura from '@primeng/themes/aura';
import { primengTranslation } from '../demo/service/primeng-translation';

/**
 * Where the topbar's calendar panel lands when it is opened.
 *
 * <p>PrimeNG positions a popover in `onAnimationStart`, before the content
 * template inside it has laid out. `absolutePosition` measures the container at
 * that moment — 21px, padding and the arrow — so its overflow guard
 * (`target.left + width > viewport.width`) finds nothing to correct. The
 * datepicker then renders, the popover grows to about 262px, and nothing moves
 * it again. In English the topbar actions sit at the right end of the bar, so
 * 126px of the calendar hung off the right edge of the window, and in
 * Arabic the panel sat 15px off its button — the width of the scrollbar, which
 * in Arabic is on the left and is not where the panel is positioned from.
 *
 * <p>The host follows the panel's size and places it whenever it changes. These
 * checks measure the result rather than the mechanism: the panel must be inside
 * the window, and it must stay attached to the button that opened it. They run
 * in both directions because the two defects appear one on each side.
 *
 * <p>The theme, the global stylesheets and the app's 12.6px root scale all apply
 * here, so the geometry measured is the geometry the user sees.
 */
@Component({
    template: `
    <div class="layout-wrapper layout-static" [class.rtl]="dir === 'rtl'" [attr.dir]="dir">
      <div class="layout-topbar" [class.rtl]="dir === 'rtl'">
        <a class="layout-topbar-logo"><span>MOHE</span></a>
        <div class="layout-topbar-menu">
          <div class="topbar-actions">
            <button class="p-link layout-topbar-button" type="button"><i class="pi pi-check-square"></i></button>
            <button #calendarButton class="p-link layout-topbar-button calendar-button" type="button"
              (click)="calendarOverlay.toggle($event)"><i class="pi pi-calendar"></i></button>
            <p-popover #calendarOverlay [dismissable]="true" appendTo="body"
              styleClass="topbar-calendar-overlay" (onShow)="onCalendarShow()"
              (onHide)="onCalendarHide()">
              <ng-template pTemplate="content">
                <p-datepicker [(ngModel)]="calendarDate" [inline]="true" [readonlyInput]="true"
                  dateFormat="dd/mm/yy"></p-datepicker>
              </ng-template>
            </p-popover>
            <button class="p-link layout-topbar-button" type="button"><i class="pi pi-user"></i></button>
            <button class="p-link layout-topbar-button" type="button"><i class="pi pi-cog"></i></button>
          </div>
        </div>
      </div>
    </div>`,
    standalone: false
})
class TopbarCalendarHostComponent {
    /** Mirrors AppTopBarComponent's two refs and its (onShow) handler. */
    @ViewChild('calendarOverlay') calendarOverlay!: Popover;
    @ViewChild('calendarButton') calendarButton!: ElementRef<HTMLElement>;
    dir: 'ltr' | 'rtl' = 'ltr';
    calendarDate: Date = new Date(2026, 7, 29);
    private panelResize?: ResizeObserver;

    onCalendarShow(): void {
        const panel = this.calendarOverlay?.container;
        if (!panel) {
            return;
        }
        this.panelResize?.disconnect();
        this.panelResize = new ResizeObserver(() => this.placeCalendarPanel());
        this.panelResize.observe(panel);
        this.placeCalendarPanel();
    }

    onCalendarHide(): void {
        this.panelResize?.disconnect();
        this.panelResize = undefined;
    }

    /** Copy of AppTopBarComponent.placeCalendarPanel — see the comment there. */
    private placeCalendarPanel(): void {
        const panel = this.calendarOverlay?.container;
        const button = this.calendarButton?.nativeElement;
        if (!panel || !button) {
            return;
        }

        const trigger = button.getBoundingClientRect();
        // The entrance animation scales the panel, so its bounding box is not its
        // layout box — measuring through it left the panel 9px off the button once
        // the scale finished. An inline `!important` outranks an animation in the
        // cascade, so the transform can be suspended for the measurement and put
        // back before anything is painted.
        const transform = panel.style.getPropertyValue('transform');
        const priority = panel.style.getPropertyPriority('transform');
        panel.style.setProperty('transform', 'none', 'important');
        const box = panel.getBoundingClientRect();
        const current = parseFloat(getComputedStyle(panel).left) || 0;
        panel.style.setProperty('transform', transform, priority);
        // The root element's box, not innerWidth: it excludes the scrollbar and,
        // in Arabic, starts at the scrollbar's width rather than at zero.
        const view = document.documentElement.getBoundingClientRect();
        const rtl = this.dir === 'rtl';

        const leading = rtl ? trigger.right - box.width : trigger.left;
        const trailing = rtl ? trigger.left : trigger.right - box.width;
        const fits = (left: number) => left >= view.left && left + box.width <= view.right;

        let wanted = leading;
        if (!fits(wanted)) {
            wanted = fits(trailing)
                ? trailing
                : Math.max(view.left, Math.min(leading, view.right - box.width));
        }

        panel.style.insetInlineStart = '';
        panel.style.insetInlineEnd = '';
        panel.style.left = `${current + (wanted - box.left)}px`;
    }
}

describe('topbar calendar panel placement', () => {

    let fixture: any;

    const build = async (dir: 'ltr' | 'rtl') => {
        TestBed.resetTestingModule();
        await TestBed.configureTestingModule({
            imports: [FormsModule, DatePickerModule, PopoverModule],
            declarations: [TopbarCalendarHostComponent],
            providers: [
                provideAnimationsAsync(),
                // The panel's size comes from the theme's tokens, so this is only
                // the app's geometry with the app's theme applied.
                providePrimeNG({
                    theme: {
                        preset: definePreset(Aura, {}),
                        options: { darkModeSelector: '.app-dark', cssLayer: { name: 'primeng', order: 'primeng' } }
                    },
                    ripple: true
                })
            ]
        }).compileComponents();

        document.documentElement.setAttribute('dir', dir);
        document.body.setAttribute('dir', dir);
        document.documentElement.classList.toggle('rtl', dir === 'rtl');
        TestBed.inject(PrimeNG).setTranslation(primengTranslation(dir === 'rtl' ? 'ar' : 'en'));

        fixture = TestBed.createComponent(TopbarCalendarHostComponent);
        fixture.componentInstance.dir = dir;
        document.body.appendChild(fixture.nativeElement);
        fixture.detectChanges();

        (fixture.nativeElement.querySelector('.calendar-button') as HTMLElement).click();
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
        // Placement follows the panel's size, so it lands once the datepicker
        // inside the popover has rendered.
        await new Promise(resolve => setTimeout(resolve, 250));
        fixture.detectChanges();
    };

    afterEach(() => {
        fixture?.nativeElement.remove();
        document.querySelectorAll('.p-popover').forEach(node => node.remove());
        document.documentElement.classList.remove('rtl');
        document.documentElement.removeAttribute('dir');
        document.body.removeAttribute('dir');
    });

    const parts = () => {
        const button = fixture.nativeElement.querySelector('.calendar-button') as HTMLElement;
        const popover = document.querySelector('.p-popover') as HTMLElement;
        expect(popover).withContext('the popover did not open').toBeTruthy();
        return { button: button.getBoundingClientRect(), panel: popover.getBoundingClientRect() };
    };

    (['ltr', 'rtl'] as const).forEach(dir => {

        it(`opens the whole panel inside the window in ${dir}`, async () => {
            await build(dir);
            const { panel } = parts();

            // The root element's box rather than innerWidth: it excludes the
            // scrollbar, which in Arabic sits on the left and shifts the band.
            const view = document.documentElement.getBoundingClientRect();

            const overshoot = panel.right - view.right;
            expect(overshoot)
                .withContext(`${dir}: ${overshoot.toFixed(0)}px of the calendar is past the right edge of the window`)
                .toBeLessThanOrEqual(0.5);
            expect(panel.left)
                .withContext(`${dir}: the calendar starts ${(view.left - panel.left).toFixed(0)}px past the left edge of the window`)
                .toBeGreaterThanOrEqual(view.left - 0.5);
        });

        it(`keeps the panel attached to the button in ${dir}`, async () => {
            // Inside the window is not enough on its own: clamping a panel to the
            // viewport edge would satisfy that and still leave it floating away
            // from the control that opened it. One of the panel's edges has to
            // line up with the matching edge of the button.
            await build(dir);
            const { button, panel } = parts();

            const anchored = Math.min(
                Math.abs(panel.left - button.left),
                Math.abs(panel.right - button.right));
            expect(anchored)
                .withContext(`${dir}: the nearest panel edge is ${anchored.toFixed(0)}px from the button`)
                .toBeLessThan(2);
        });

        it(`hangs the panel below the button in ${dir}`, async () => {
            await build(dir);
            const { button, panel } = parts();

            const above = button.bottom - panel.top;
            expect(panel.top)
                .withContext(`${dir}: the panel starts ${above.toFixed(0)}px above the button's bottom edge`)
                .toBeGreaterThanOrEqual(button.bottom - 1);
        });
    });
});
