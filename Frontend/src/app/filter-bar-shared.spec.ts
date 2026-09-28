import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzInputNumberModule } from 'ng-zorro-antd/input-number';
import { NzSelectModule } from 'ng-zorro-antd/select';

/**
 * The shared filter bar, defined in assets/layout/styles/_filter-bar.scss.
 *
 * <p>Ten list pages had each laid their filters out by hand and none of them
 * survived a narrow screen. The same three faults recurred everywhere:
 * controls carrying their own pixel widths, so a row of them could never
 * divide evenly; the buttons sitting in the same wrapping flex row as the
 * fields, so they landed wherever there happened to be space; and filters
 * sharing the page header with Add and Export. One stylesheet now answers all
 * three, which is also why it is worth testing — a regression here is a
 * regression on every one of those pages at once.
 *
 * <p>The assertions are geometric, against the real global stylesheet (Karma
 * loads styles.scss), because the failures are geometric: nothing in the
 * compiler can see a row that wraps badly. They deliberately do not repeat any
 * page's own markup, which would only agree with itself.
 */
@Component({
    template: `
    <div class="filter-bar" [attr.dir]="dir" [style.width.px]="width">
      <div class="filter-bar__grid" [class]="gridClass">
        @for (f of fields; track f) {
          <div class="filter-bar__field"
               [class.filter-bar__field--wide]="f === wide"
               [class.filter-bar__field--active]="f === active">
            <label [attr.for]="f">{{ f }}</label>
            <input [id]="f" class="p-inputtext" type="text" />
          </div>
        }
      </div>
      <div class="filter-bar__actions">
        <button type="button">Search</button>
        <button type="button">Clear</button>
      </div>
    </div>
  `,
    standalone: false
})
class FilterBarHostComponent {
    fields = ['one', 'two', 'three', 'four'];
    gridClass = 'filter-bar__grid filter-bar__grid--cols-4';
    wide: string | null = null;
    active: string | null = null;
    dir = 'ltr';
    width = 1000;
}

/**
 * The same bar with ng-zorro controls in it — what the Staff-access pages put
 * there. Four shapes: a search box in an affix wrapper, a select, a bare
 * nz-input, and a number input. The first three fill their column from
 * ng-zorro's own stylesheet; the number input is the one that does not.
 */
@Component({
    template: `
    <div class="filter-bar" style="width: 1000px">
      <div class="filter-bar__grid filter-bar__grid--cols-4">
        <div class="filter-bar__field">
          <label for="zSearch">search</label>
          <nz-input-group [nzSuffix]="suffix">
            <input id="zSearch" nz-input type="text" />
          </nz-input-group>
          <ng-template #suffix><span>x</span></ng-template>
        </div>
        <div class="filter-bar__field">
          <label for="zKind">kind</label>
          <nz-select id="zKind" [ngModel]="''">
            <nz-option nzLabel="All" nzValue=""></nz-option>
          </nz-select>
        </div>
        <div class="filter-bar__field">
          <label for="zPlain">plain</label>
          <input id="zPlain" nz-input type="text" />
        </div>
        <div class="filter-bar__field">
          <label for="zNumber">number</label>
          <nz-input-number id="zNumber" [ngModel]="null"></nz-input-number>
        </div>
      </div>
    </div>
  `,
    standalone: false
})
class NzFilterBarHostComponent {}

describe('shared filter bar', () => {

    let fixture: any;
    let host: FilterBarHostComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [FilterBarHostComponent, NzFilterBarHostComponent],
            imports: [FormsModule, NzInputModule, NzInputNumberModule, NzSelectModule],
            providers: [provideNoopAnimations()]
        }).compileComponents();

        fixture = TestBed.createComponent(FilterBarHostComponent);
        host = fixture.componentInstance;
        // Attached to the document, or nothing has a computed layout at all.
        document.body.appendChild(fixture.nativeElement);
        fixture.detectChanges();
    });

    afterEach(() => fixture.nativeElement.remove());

    const el = (sel: string) => fixture.nativeElement.querySelector(sel) as HTMLElement;
    const all = (sel: string) =>
        Array.from(fixture.nativeElement.querySelectorAll(sel)) as HTMLElement[];
    const tracks = () =>
        getComputedStyle(el('.filter-bar__grid')).gridTemplateColumns.split(/\s+/);

    it('is a grid at all', () => {
        // Without the stylesheet loaded every other assertion here would pass
        // or fail for the wrong reason, so establish it first.
        expect(getComputedStyle(el('.filter-bar__grid')).display).toBe('grid');
    });

    it('declares as many columns as the modifier asks for', () => {
        // Viewport-independent on purpose: measuring where the fields happen to
        // land at one width proves nothing, because a wrong column count and a
        // right one agree at some widths.
        expect(tracks().length).withContext(`tracks: ${tracks().join(' | ')}`).toBe(4);

        host.fields = ['a', 'b', 'c', 'd', 'e', 'f'];
        host.gridClass = 'filter-bar__grid filter-bar__grid--cols-6';
        fixture.detectChanges();

        expect(tracks().length).withContext(`tracks: ${tracks().join(' | ')}`).toBe(6);
    });

    it('never leaves a field stranded on a row of its own', () => {
        // The auto-fit failure this replaced: a column count chosen from the
        // available width strands whatever does not divide into it.
        //
        // The container is deliberately narrow. Declared columns divide it
        // however tight it gets, whereas auto-fit stops fitting more tracks
        // and drops the remainder onto a second row — at a roomy width the two
        // agree and the test would be guarding nothing.
        host.width = 600;
        const cases: Array<[number, string]> = [[2, 'cols-2'], [3, 'cols-3'], [5, 'cols-5']];

        for (const [n, cls] of cases) {
            host.fields = Array.from({ length: n }, (_, i) => `f${i}`);
            host.gridClass = `filter-bar__grid filter-bar__grid--${cls}`;
            fixture.detectChanges();

            const rows = new Set(all('.filter-bar__field')
                .map(f => Math.round(f.getBoundingClientRect().top)));

            expect(rows.size).withContext(`${n} fields over ${cls} should be one row`).toBe(1);
        }
    });

    it('gives a --wide field two of the tracks', () => {
        host.wide = 'one';
        fixture.detectChanges();

        const widths = all('.filter-bar__field').map(f => f.getBoundingClientRect().width);

        // Two tracks plus the gap between them: strictly more than double is
        // wrong, and exactly equal would mean the modifier did nothing.
        expect(widths[0]).withContext(`widths: ${widths}`).toBeGreaterThan(widths[1] * 1.9);
        expect(widths[0]).toBeLessThan(widths[1] * 2.5);
    });

    it('lets each control fill its column instead of a fixed width', () => {
        all('.filter-bar__field .p-inputtext').forEach((input, i) => {
            const field = input.parentElement as HTMLElement;

            expect(Math.round(input.getBoundingClientRect().width))
                .withContext(`field ${i} control does not fill its column`)
                .toBe(Math.round(field.getBoundingClientRect().width));
        });
    });

    it('lets an ng-zorro control fill its column too', () => {
        // The Staff-access pages had each search box at `min-width: 260px` and
        // each select at its own pixel width — not because ng-zorro sized them
        // small (its stylesheet gives an input and a select the full width)
        // but because the toolbar they sat in was a bare div. The one shape
        // that is intrinsically narrow is the number input, ~90px on its own;
        // the fill list is explicit, so it has to be named there.
        fixture.nativeElement.remove();
        fixture = TestBed.createComponent(NzFilterBarHostComponent);
        document.body.appendChild(fixture.nativeElement);
        fixture.detectChanges();

        all('.filter-bar__field').forEach((field, i) => {
            const control = field.querySelector('label + *') as HTMLElement;
            expect(Math.round(control.getBoundingClientRect().width))
                .withContext(`ng-zorro field ${i} (${control.tagName.toLowerCase()}) does not fill its column`)
                .toBe(Math.round(field.getBoundingClientRect().width));
        });
    });

    it('keeps the actions out of the field flow', () => {
        // They used to be one more item in the same wrapping row, so they
        // wrapped under whichever field ran out of room first.
        const grid = el('.filter-bar__grid').getBoundingClientRect();
        const actions = el('.filter-bar__actions').getBoundingClientRect();

        expect(actions.top).toBeGreaterThanOrEqual(grid.bottom);
    });

    it('marks only the filter that is set', () => {
        const colourOf = (name: string) => {
            const field = all('.filter-bar__field')
                .find(f => (f.querySelector('label') as HTMLElement).textContent?.trim() === name)!;
            return getComputedStyle(field.querySelector('label') as HTMLElement).color;
        };

        const idle = colourOf('one');
        host.active = 'one';
        fixture.detectChanges();

        expect(colourOf('one')).withContext('an active filter should say so').not.toBe(idle);
        expect(colourOf('two')).withContext('an unset filter should not').toBe(idle);
    });

    it('mirrors for Arabic without a second set of rules', () => {
        // The spacer is a logical margin, so `dir` alone moves it. A physical
        // margin-left would strand the trailing content — a result count — on
        // the wrong side of every Arabic page.
        //
        // Asserted by position rather than by reading the computed margin:
        // getComputedStyle resolves an `auto` margin to its used pixel value
        // once the element is laid out, so the string 'auto' only comes back
        // some of the time.
        const spacer = document.createElement('span');
        spacer.className = 'filter-bar__hint filter-bar__actions-spacer';
        spacer.textContent = '42 records';
        el('.filter-bar__actions').appendChild(spacer);
        fixture.detectChanges();

        const buttons = () => all('.filter-bar__actions button')
            .map(b => b.getBoundingClientRect());
        const gap = () => {
            const box = spacer.getBoundingClientRect();
            const last = buttons()[buttons().length - 1];
            return { spacer: box, last };
        };

        // Left to right the spacer is pushed past the buttons.
        expect(gap().spacer.left)
            .withContext('the hint should be pushed to the trailing end')
            .toBeGreaterThan(gap().last.right);

        host.dir = 'rtl';
        fixture.detectChanges();

        // Right to left the trailing end is the other one.
        expect(gap().spacer.right)
            .withContext('the trailing end mirrors with dir')
            .toBeLessThan(gap().last.left);
    });
});
