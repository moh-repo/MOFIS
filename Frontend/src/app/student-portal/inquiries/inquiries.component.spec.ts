import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterModule } from '@angular/router';
import { of } from 'rxjs';
import { InquiriesComponent } from './inquiries.component';
import { StudentPortalModule } from '../student-portal.module';
import { StudentPortalApiService } from '../student-portal-api.service';
import { TranslationService } from '../../demo/service/translation.service';
import { ToastService } from '../../demo/service/toast.service';
import { CodeListService } from '../../demo/service/code-list.service';
import { codeListStub, codeRow } from '../../testing/code-list.stub';
import { NZ_ICONS } from 'ng-zorro-antd/icon';
import { MessageOutline, PlusOutline } from '@ant-design/icons-angular/icons';

/**
 * The inquiry type was a free-text box; it is a select over the inquiry-type
 * list, defaulting to the list's first row, and statuses are named and
 * coloured by their list rows.
 */
describe('InquiriesComponent', () => {
    let fixture: ComponentFixture<InquiriesComponent>;
    let component: InquiriesComponent;
    let createInquiry: jasmine.Spy;

    beforeEach(async () => {
        createInquiry = jasmine.createSpy('createInquiry').and.returnValue(of({}));
        await TestBed.configureTestingModule({
            imports: [StudentPortalModule, RouterModule.forRoot([])],
            providers: [
                // The app registers ng-zorro's icons in AppModule; the portal module alone does not.
                { provide: NZ_ICONS, useValue: [MessageOutline, PlusOutline] },
                {
                    provide: StudentPortalApiService,
                    useValue: {
                        getInquiries: () => of({ content: [{ id: 1, studentId: 1, type: 'FINANCE', title: 'Fee', status: 'RESPONDED', createdAt: '2026-08-01', updatedAt: '2026-08-02' }], totalElements: 1, totalPages: 1 }),
                        getInquiry: (id: number) => of({ id, type: 'FINANCE', title: 'Fee', status: 'RESPONDED', createdAt: '2026-08-01', updatedAt: '2026-08-02' }),
                        createInquiry
                    }
                },
                { provide: TranslationService, useValue: { currentLanguage$: of('en'), initialized$: of(true), isReady: () => true, isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en' } },
                { provide: ToastService, useValue: { error: () => {}, success: () => {}, warning: () => {} } },
                {
                    provide: CodeListService,
                    useValue: codeListStub({
                        'inquiry-type': [codeRow('GENERAL', { labelEn: 'General', sortOrder: 1 }), codeRow('FINANCE', { labelEn: 'Finance', sortOrder: 2 }), codeRow('OLD', { active: false, sortOrder: 0 })],
                        'inquiry-status': [codeRow('RESPONDED', { labelEn: 'Responded', uiSeverity: 'success' }), codeRow('OPEN', { labelEn: 'Open', uiSeverity: 'info' })]
                    })
                }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(InquiriesComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('offers the active inquiry types as a select and defaults to the first', () => {
        expect(component.typeOptions.map(o => o.value)).toEqual(['GENERAL', 'FINANCE']);
        expect(component.newType).toBe('GENERAL');
        component.openForm();
        fixture.detectChanges();
        expect(document.querySelector('nz-modal-container nz-select, .inquiry-type-select')).withContext('a select, not a text box').not.toBeNull();
        expect(document.querySelector('nz-modal-container input[name="newType"]')).toBeNull();
    });

    it('names and colours the status from its list row', () => {
        expect(component.inquiryTypeLabel('FINANCE')).toBe('Finance');
        expect(component.inquiryStatusLabel('RESPONDED')).toBe('Responded');
        expect(component.statusColor('RESPONDED')).toBe('success');
        expect(component.statusColor('OPEN')).toBe('processing');
        expect(component.statusColor('NOPE')).toBe('default');
        expect(fixture.nativeElement.textContent).toContain('Responded');
    });

    it('submits the selected type', async () => {
        component.newType = 'FINANCE';
        component.newTitle = 'Question';
        await component.submit();
        expect(createInquiry).toHaveBeenCalledWith('FINANCE', 'Question', undefined);
    });

    afterEach(() => {
        document.querySelectorAll('nz-modal-container, .cdk-overlay-container').forEach(e => e.remove());
    });

    // Live QA M19: a title the student typed with markup was rendered as markup in the
    // detail window's heading. It is shown as the text that was typed.
    it('shows an inquiry title with markup as text in the detail heading', () => {
        component.selected = { id: 2, type: 'FINANCE', title: '<b>bold</b><img src=x>', status: 'OPEN' } as any;
        component.showDetailModal = true;
        fixture.detectChanges();

        const heading = document.querySelector('nz-modal-container .ant-modal-title') as HTMLElement;
        expect(heading.textContent?.trim()).toBe('<b>bold</b><img src=x>');
        expect(heading.querySelector('b, img')).toBeNull();

        component.closeDetail();
        fixture.detectChanges();
    });
});
