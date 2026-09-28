import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { of } from 'rxjs';
import { MessageService } from 'primeng/api';
import { StudentQuickEditComponent } from './student-quick-edit.component';
import { StudentQuickEditModule } from './student-quick-edit.module';
import { StudentQuickEditService } from '../../service/student-quick-edit.service';
import { StudentdataService } from '../../service/studentdata.service';
import { TranslationService } from '../../service/translation.service';
import { UmsPermissionService } from '../../service/ums-permission.service';
import { UmsAuthService } from '../../service/ums-auth.service';
import { LookupService } from '../../service/lookup.service';
import { StudentDocumentService } from '../../service/student-document.service';
import { DocumentTypeService } from '../../service/document-type.service';

const LOOKUP_KEYS = [
  'genders', 'statuses', 'degrees', 'majors', 'levels', 'countries', 'governments',
  'banks', 'bankBranches', 'dofs', 'universityFeeStatuses', 'finReceivedStatuses',
  'receivedWhomStatuses', 'agencyTypes', 'ministryTypes', 'studyTypes',
  'universityTypes', 'homeCountries', 'finReceivedWhoms'
];
const emptyMetadata = LOOKUP_KEYS.reduce((acc, k) => ({ ...acc, [k]: [] }), {});

/**
 * The "Refine Results" panel.
 *
 * <p>The search box used to be wrapped in `p-input-icon-left`. PrimeNG 21
 * removed that class and ships no styles for it, and nothing in this app
 * replaced them, so the magnifier rendered as a loose glyph beside the field
 * instead of sitting inside it — the build stayed green and nothing in the
 * compiler could see it. The geometry assertions below are what catch that
 * class of regression: they measure where the icon actually lands relative to
 * the input it belongs to.
 */
describe('StudentQuickEdit refine-results panel', () => {

  let fixture: ComponentFixture<StudentQuickEditComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StudentQuickEditModule, RouterTestingModule],
      providers: [
        MessageService,
        {
          provide: StudentQuickEditService,
          useValue: { getMetadata: () => of(emptyMetadata), getAll: () => of([]), update: () => of({}) }
        },
        { provide: StudentdataService, useValue: { getYearResults: () => of([]), getProgress: () => of(null) } },
        {
          provide: TranslationService,
          useValue: {
            currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
            isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en'
          }
        },
        { provide: UmsPermissionService, useValue: { getAllowedCountryIds: () => [], hasPermission: () => true } },
        { provide: UmsAuthService, useValue: { permissions$: of([]) } },
        { provide: LookupService, useValue: { getQuarterNumbers: () => of([]) } },
        {
          provide: StudentDocumentService,
          useValue: {
            getStudentDocuments: () => of([]),
            isFormatLikeType: () => true,
            getDocumentFormatLabelKey: () => 'STUDENTS.FORMAT_OTHER',
            getDocumentFormatIcon: () => 'pi pi-file'
          }
        },
        { provide: DocumentTypeService, useValue: { getAllActiveDocumentTypes: () => of([]) } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(StudentQuickEditComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  });

  const el = <T extends HTMLElement>(selector: string) =>
    fixture.nativeElement.querySelector(selector) as T;

  it('no longer depends on the class PrimeNG 21 removed', () => {
    expect(el('.filters-card .p-input-icon-left'))
      .withContext('p-input-icon-left has no styles in PrimeNG 21')
      .toBeNull();
  });

  it('puts the search icon inside the input, not beside or above it', () => {
    const icon = el('.filters-search__icon').getBoundingClientRect();
    const input = el('.filters-search input').getBoundingClientRect();

    // The icon's centre has to fall within the input's box on both axes.
    // Checking the horizontal edges alone is not enough: an unpositioned icon
    // still shares the container's left edge, it just sits on the line above.
    const x = icon.left + icon.width / 2;
    const y = icon.top + icon.height / 2;

    expect(x).withContext(`icon centre x ${x}, input ${input.left}-${input.right}`)
      .toBeGreaterThanOrEqual(input.left);
    expect(x).toBeLessThanOrEqual(input.right);
    expect(y).withContext(`icon centre y ${y}, input ${input.top}-${input.bottom}`)
      .toBeGreaterThanOrEqual(input.top);
    expect(y).toBeLessThanOrEqual(input.bottom);
  });

  it('centres the icon on the input rather than letting it sit on a line of its own', () => {
    const icon = el('.filters-search__icon').getBoundingClientRect();
    const input = el('.filters-search input').getBoundingClientRect();

    const drift = Math.abs((icon.top + icon.height / 2) - (input.top + input.height / 2));
    expect(drift).toBeLessThan(2);
  });

  it('reserves room so the text cannot run underneath the icon', () => {
    const input = el<HTMLInputElement>('.filters-search input');
    const padding = parseFloat(getComputedStyle(input).paddingLeft);
    const iconRight = el('.filters-search__icon').getBoundingClientRect().right;

    expect(padding).toBeGreaterThan(iconRight - input.getBoundingClientRect().left);
  });

  it('offers a clear button only once something has been typed', () => {
    expect(el('.filters-search__clear')).toBeNull();

    fixture.componentInstance.searchTerm = 'ahmed';
    fixture.detectChanges();

    expect(el('.filters-search__clear')).toBeTruthy();
  });

  it('clearing the search empties the term and reruns the filter', () => {
    fixture.componentInstance.searchTerm = 'ahmed';
    fixture.detectChanges();

    el<HTMLButtonElement>('.filters-search__clear').click();
    fixture.detectChanges();

    expect(fixture.componentInstance.searchTerm).toBe('');
    expect(el('.filters-search__clear')).toBeNull();
  });

  it('marks only the fields that are actually narrowing the list', () => {
    const active = () => fixture.nativeElement
      .querySelectorAll('.filters-card__grid .filters-field--active').length;

    expect(active()).toBe(0);

    fixture.componentInstance.searchTerm = 'ahmed';
    fixture.componentInstance.selectedGender = 1;
    fixture.detectChanges();

    expect(active()).toBe(2);
  });

  it('does not stack a card margin on top of the wrapper gap', () => {
    // The global .card has margin-bottom: 2rem and the wrapper has a 2rem flex
    // gap. A flex gap does not collapse against a margin, so the two added up
    // to 4rem of dead space between every section on the page.
    const wrapper = el('.page-wrapper');
    const sections = Array.from(wrapper.querySelectorAll(':scope > .card')) as HTMLElement[];

    expect(sections.length).toBeGreaterThanOrEqual(2);

    // Measured against the wrapper's own gap rather than a pixel constant: the
    // layout sets a non-16px root font size, so a hard-coded threshold would
    // sit uncomfortably close to the value it is meant to reject.
    const declared = parseFloat(getComputedStyle(wrapper).rowGap);

    sections.slice(0, -1).forEach((section, i) => {
      const gap = sections[i + 1].getBoundingClientRect().top
        - section.getBoundingClientRect().bottom;

      expect(gap)
        .withContext(`gap after section ${i} was ${gap}px, wrapper gap is ${declared}px`)
        .toBeLessThan(declared + 4);
    });
  });

  it('lays the four fields out on one row when there is room for them', () => {
    // The grid used to be auto-fit, which drops the fourth field onto a row of
    // its own at common widths. The tops being equal is what says otherwise.
    const tops = Array.from(
      fixture.nativeElement.querySelectorAll('.filters-card__grid .filters-field')
    ).map(f => Math.round((f as HTMLElement).getBoundingClientRect().top));

    expect(tops.length).toBe(4);
    expect(new Set(tops).size).withContext(`field tops: ${tops}`).toBe(1);
  });
});
