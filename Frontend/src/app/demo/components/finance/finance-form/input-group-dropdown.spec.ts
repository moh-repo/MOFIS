import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { SelectModule } from 'primeng/select';
import { ButtonModule } from 'primeng/button';

/**
 * The "Received Whom" field puts a p-select and two icon buttons in a
 * p-inputgroup inside a `p-fluid` wrapper, and the select has to span the row
 * that the two buttons leave it.
 *
 * Two separate things used to get this wrong, both now fixed:
 *
 * 1. Up to PrimeNG 17, `.p-inputgroup .p-inputwrapper > .p-component` carried
 *    `flex: 1 1 auto; width: 1%`, where the 1% was only ever meant as a
 *    flex-basis. The p-select host computed to `display: block`, so the flex
 *    declaration was inert and the 1% applied literally, leaving the control a
 *    few pixels wide. finance-form.component.scss forced `display: flex` on the
 *    host to work around it. PrimeNG 21 dropped the `width: 1%` rule, so the
 *    workaround was removed — it had started shrink-wrapping the whole group.
 *
 * 2. `p-fluid` was a globally styled class until v18, when it became a
 *    component and the class stopped being styled. styles.scss now restores the
 *    class behaviour; without it the select sits at its content width (~53px of
 *    a 400px row) rather than filling.
 *
 * So this asserts the outcome both of those affect: the control fills the space
 * beside the buttons. It measures the inner .p-select, not the host, because the
 * host stretches either way and would not catch the regression.
 */
@Component({
    template: `
    <div class="p-fluid" style="width: 400px">
      <div class="p-inputgroup" data-testid="group">
        <p-select [options]="options" [(ngModel)]="value" optionLabel="label"
                    optionValue="value" placeholder="Select"></p-select>
        <button pButton type="button" icon="pi pi-pencil"></button>
        <button pButton type="button" icon="pi pi-plus"></button>
      </div>
    </div>
  `,
    standalone: false
})
class InputGroupHostComponent {
  options = [{ label: 'Recipient one', value: 1 }];
  value: number | null = null;
}

describe('p-select inside p-inputgroup', () => {
  let fixture: any;

  function innerWidth(testId: string): number {
    const group = fixture.nativeElement.querySelector(`[data-testid="${testId}"]`) as HTMLElement;
    return (group.querySelector('.p-select') as HTMLElement).getBoundingClientRect().width;
  }

  function groupWidth(testId: string): number {
    const group = fixture.nativeElement.querySelector(`[data-testid="${testId}"]`) as HTMLElement;
    return group.getBoundingClientRect().width;
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [InputGroupHostComponent],
      imports: [FormsModule, SelectModule, ButtonModule]
    }).compileComponents();

    fixture = TestBed.createComponent(InputGroupHostComponent);
    // Attach to the document so layout is actually computed.
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();
  });

  afterEach(() => fixture.nativeElement.remove());

  it('keeps the input group spanning its container', () => {
    // The workaround this replaced collapsed the group to ~84px of the 400.
    expect(groupWidth('group')).toBeGreaterThan(300);
  });

  it('fills the row beside the buttons', () => {
    // Without the p-fluid rules the control sits at its content width, ~53px.
    expect(innerWidth('group')).toBeGreaterThan(groupWidth('group') * 0.6);
  });
});
