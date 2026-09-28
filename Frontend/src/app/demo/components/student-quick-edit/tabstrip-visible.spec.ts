import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { of } from 'rxjs';
import { MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
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

// The PrimeNG 21 Tabs API has no `styleClass` input (v17's p-tabView did), so
// `styleClass="edit-tabview"` silently stopped reaching the host and every layout
// rule scoped under .edit-tabview went dead — the tab strip lost its padding,
// background and active underline, and the panels lost their own scroll container.
describe('StudentQuickEdit edit dialog tab strip', () => {
  let fixture: ComponentFixture<StudentQuickEditComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StudentQuickEditModule, RouterTestingModule],
      providers: [
        MessageService,
        // As in app.module.ts: every dialog lands on <body>, outside the component
        // host, which is what the layout assertions below depend on.
        providePrimeNG({ overlayAppendTo: 'body' }),
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
    fixture.componentInstance.editStudent({ studentId: 1, studentName: 'Test' } as any);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  });

  it('renders all seven tab headers', () => {
    expect(document.querySelectorAll('p-tabs .p-tab').length).toBe(7);
  });

  it('hosts the shared documents component in the Documents panel', () => {
    expect(document.querySelector('p-tabs app-student-documents')).toBeTruthy();
  });

  it('applies the edit-tabview class to the p-tabs host so its layout rules bind', () => {
    const host = document.querySelector('p-tabs') as HTMLElement;
    expect(host.classList).toContain('edit-tabview');
  });

  it('gives the tab strip a visible height', () => {
    const tablist = document.querySelector('p-tabs .p-tablist') as HTMLElement;
    expect(tablist.getBoundingClientRect().height).toBeGreaterThan(0);
  });

  // providePrimeNG appends every dialog to <body>, so the rules that let the tab
  // panels scroll cannot live under :host ::ng-deep; they are global, in
  // assets/layout/styles/_student-quick-edit-dialog.scss. While they were
  // component-scoped they never reached the dialog, and its inline
  // overflow: hidden clipped the form at the footer with no scrollbar
  // (reported 24 Sep 2026).
  it('renders the edit dialog outside the component host', () => {
    const dialog = document.querySelector('.p-dialog.student-edit-dialog') as HTMLElement;
    expect(dialog).toBeTruthy();
    expect((fixture.nativeElement as HTMLElement).contains(dialog)).toBeFalse();
  });

  it('lets the tab panels scroll inside a flex content area', () => {
    const content = document.querySelector('.p-dialog.student-edit-dialog .p-dialog-content') as HTMLElement;
    const panels = document.querySelector('.p-dialog.student-edit-dialog .p-tabpanels') as HTMLElement;
    expect(getComputedStyle(content).display).toBe('flex');
    expect(getComputedStyle(content).flexDirection).toBe('column');
    expect(getComputedStyle(panels).overflowY).toBe('auto');
    expect(getComputedStyle(panels).minHeight).toBe('0px');
  });
});
