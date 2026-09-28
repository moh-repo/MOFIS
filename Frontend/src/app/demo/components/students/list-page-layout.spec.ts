import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TableModule } from 'primeng/table';

/**
 * Layout guards for the list pages.
 *
 * The theme-variable test is the one that earns its keep: the variables the
 * layout is built on (--surface-card, --text-color, --surface-border,
 * --primary-color, ...) came from the prebuilt theme.css that PrimeNG 18
 * removed, and roughly 1000 declarations read them. With them undefined the
 * chrome lost its colours, borders and spacing while the build stayed green —
 * nothing in the compiler can see that. They are redefined in
 * assets/layout/styles/_legacy-theme-vars.scss, and this fails without it.
 *
 * The table-width test guards an invariant rather than a specific fix: PrimeNG
 * 21 stretches .p-datatable-table on its own (measured — removing the v17-style
 * `width: 100%` override changed nothing, so no such override is carried). It
 * is here so that if a future version reverts to shrink-to-fit sizing, the list
 * pages do not quietly narrow again.
 */
@Component({
    template: `
    <div class="card" data-testid="card" style="width: 900px">
      <p-table [value]="rows" [tableStyle]="{'min-width': '10rem'}">
        <ng-template pTemplate="header">
          <tr><th>Name</th><th>Major</th></tr>
        </ng-template>
        <ng-template pTemplate="body" let-row>
          <tr><td>{{ row.name }}</td><td>{{ row.major }}</td></tr>
        </ng-template>
      </p-table>
    </div>
  `,
    standalone: false
})
class ListPageHostComponent {
  rows = [{ name: 'Student one', major: 'Engineering' }];
}

describe('list page layout', () => {
  let fixture: any;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ListPageHostComponent],
      imports: [TableModule]
    }).compileComponents();

    fixture = TestBed.createComponent(ListPageHostComponent);
    // Attach to the document so layout is actually computed.
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();
  });

  afterEach(() => fixture.nativeElement.remove());

  it('stretches the table across its container', () => {
    const card = fixture.nativeElement.querySelector('[data-testid="card"]') as HTMLElement;
    const table = fixture.nativeElement.querySelector('.p-datatable-table') as HTMLElement;

    expect(table).withContext('.p-datatable-table should exist').toBeTruthy();
    // Fills the card's content box: 842 of 900, the rest being the card padding.
    // Shrink-to-fit would render this two-column table a few hundred pixels wide.
    expect(table.getBoundingClientRect().width)
      .toBeGreaterThan(card.getBoundingClientRect().width * 0.9);
  });

  it('defines the theme variables the layout is built on', () => {
    const root = getComputedStyle(document.documentElement);
    for (const name of ['--surface-card', '--surface-border', '--text-color',
                        '--text-color-secondary', '--primary-color', '--surface-ground']) {
      expect(root.getPropertyValue(name).trim())
        .withContext(`${name} should resolve`).not.toBe('');
    }
  });
});
