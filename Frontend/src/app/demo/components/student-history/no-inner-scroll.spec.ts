import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TableModule } from 'primeng/table';

/**
 * The student history page had its own scroll containers inside .layout-main —
 * a 400px-capped table and a height:100% chain that fixed the page to the
 * viewport — so the content scrolled inside the card instead of the window
 * scrolling. The table keeps [scrollable]="true", which is what gives the
 * container overflow handling for its fixed-width columns, but no scrollHeight.
 *
 * PrimeNG maps scrollHeight straight onto the container's max-height
 * (inlineStyles.tableContainer in primeng/table), so this is the assertion that
 * dropping it leaves the table free to grow: with the attribute back, the same
 * 30 rows scroll inside a 400px box.
 */
@Component({
    template: `
    <div style="width: 900px">
      <p-table [value]="rows" [scrollable]="true" [tableStyle]="{'min-width': '100%'}">
        <ng-template pTemplate="header">
          <tr><th>Name</th></tr>
        </ng-template>
        <ng-template pTemplate="body" let-row>
          <tr><td>{{ row.name }}</td></tr>
        </ng-template>
      </p-table>
    </div>
  `,
    standalone: false
})
class ScrollableHostComponent {
  rows = Array.from({ length: 30 }, (_, i) => ({ name: `Row ${i}` }));
}

describe('student history table scrolling', () => {
  let fixture: any;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ScrollableHostComponent],
      imports: [TableModule]
    }).compileComponents();

    fixture = TestBed.createComponent(ScrollableHostComponent);
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();
  });

  afterEach(() => fixture.nativeElement.remove());

  it('grows with its rows instead of scrolling inside a fixed box', () => {
    const container = fixture.nativeElement
      .querySelector('.p-datatable-table-container') as HTMLElement;
    expect(container).withContext('table container should exist').toBeTruthy();

    expect(getComputedStyle(container).maxHeight)
      .withContext('scrollHeight would set a max-height here').toBe('none');
    // 30 rows are far taller than the 400px cap that used to be here.
    expect(container.scrollHeight)
      .withContext(`content ${container.scrollHeight}px in ${container.clientHeight}px box`)
      .toBeLessThanOrEqual(container.clientHeight + 1);
  });
});
