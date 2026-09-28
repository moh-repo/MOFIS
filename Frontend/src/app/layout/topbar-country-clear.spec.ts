import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { SelectModule } from 'primeng/select';

/**
 * The clear "×" on the topbar's country dropdown.
 *
 * <p>It sat above the chevron beside it. Not by much — eight pixels — but the
 * two icons are adjacent in a 2.5rem-tall control, so it read as broken.
 *
 * <p>The cause was three rules written against a version of the component that
 * no longer exists. PrimeNG used to position the × absolutely: `inset-inline-end`
 * placed it, `margin-top: -0.5rem` was the other half of the usual `top: 50%`
 * centring trick, and the label reserved padding so the value would not run
 * underneath it. PrimeNG 21 lays the control out as a flex row instead — label,
 * clear icon, dropdown — and centres the icon with `align-self: center`.
 *
 * <p>So `inset-inline-end` stopped doing anything at all (it applies only to
 * positioned elements), and the negative margin stopped cancelling a `top: 50%`
 * that was no longer there — it just lifted the icon. This is the failure mode
 * of an override that compensates for someone else's implementation: it does
 * not break when that implementation changes, it silently becomes the bug.
 *
 * <p>Measured against the chevron rather than against a number, because what
 * "aligned" means here is "level with the control beside it", and that survives
 * a change of theme, font size or padding.
 */
@Component({
    template: `
    <div class="topbar-country-selector" [attr.dir]="dir">
      <p-select [options]="options" [(ngModel)]="selected"
        optionLabel="name" optionValue="id"
        [showClear]="true" styleClass="topbar-country-dropdown"></p-select>
    </div>`,
    standalone: false
})
class CountrySelectorHostComponent {
    dir: 'ltr' | 'rtl' = 'ltr';
    options = [{ id: 1, name: 'مصر' }, { id: 2, name: 'الأردن' }];
    /** Selected, or PrimeNG renders no clear icon at all. */
    selected: number | null = 1;
}

describe('topbar country selector clear icon', () => {

    let fixture: any;

    const build = async (dir: 'ltr' | 'rtl') => {
        TestBed.resetTestingModule();
        await TestBed.configureTestingModule({
            imports: [FormsModule, SelectModule],
            declarations: [CountrySelectorHostComponent]
        }).compileComponents();

        fixture = TestBed.createComponent(CountrySelectorHostComponent);
        fixture.componentInstance.dir = dir;
        document.body.appendChild(fixture.nativeElement);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    };

    afterEach(() => fixture?.nativeElement.remove());

    const parts = () => {
        const host: HTMLElement = fixture.nativeElement;
        const clear = host.querySelector('.p-select-clear-icon') as HTMLElement;
        const dropdown = host.querySelector('.p-select-dropdown') as HTMLElement;
        expect(clear).withContext('no clear icon rendered — is [showClear] still set?').toBeTruthy();
        return { clear: clear.getBoundingClientRect(), dropdown: dropdown.getBoundingClientRect() };
    };

    (['ltr', 'rtl'] as const).forEach(dir => {

        it(`sits level with the dropdown chevron in ${dir}`, async () => {
            await build(dir);
            const { clear, dropdown } = parts();

            const drift = (clear.top + clear.height / 2) - (dropdown.top + dropdown.height / 2);
            expect(Math.abs(drift))
                .withContext(`${dir}: the × is ${drift.toFixed(1)}px off the chevron's centre line`)
                .toBeLessThan(1.5);
        });

        it(`stays inside the control in ${dir}`, async () => {
            // A negative margin large enough to centre nothing can also push the
            // icon out of the box entirely.
            await build(dir);
            const host: HTMLElement = fixture.nativeElement;
            const box = (host.querySelector('.p-select') as HTMLElement).getBoundingClientRect();
            const { clear } = parts();

            expect(clear.top).withContext(`${dir}: the × overflows the top edge`).toBeGreaterThanOrEqual(box.top - 0.5);
            expect(clear.bottom).withContext(`${dir}: the × overflows the bottom edge`).toBeLessThanOrEqual(box.bottom + 0.5);
        });
    });

    it('keeps the value beside the × rather than a reserved gap away from it', async () => {
        // The label used to reserve 3.5rem of padding for an icon that no longer
        // overlaps it, which left a wide empty strip between the country name
        // and the ×.
        await build('rtl');
        const host: HTMLElement = fixture.nativeElement;
        const label = host.querySelector('.p-select-label') as HTMLElement;
        const style = getComputedStyle(label);

        // In RTL the reserved side was padding-left; in LTR, padding-right.
        expect(parseFloat(style.paddingLeft))
            .withContext(`label padding-left is ${style.paddingLeft}`)
            .toBeLessThan(24);
    });
});
