import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterModule } from '@angular/router';
import { of } from 'rxjs';
import { ExtensionsComponent } from './extensions.component';
import { StudentPortalModule } from '../student-portal.module';
import { PortalExtension, StudentPortalApiService } from '../student-portal-api.service';
import { TranslationService } from '../../demo/service/translation.service';
import { CodeListService } from '../../demo/service/code-list.service';
import { codeListStub, codeRow } from '../../testing/code-list.stub';
import { NZ_ICONS } from 'ng-zorro-antd/icon';
import { CalendarOutline, EyeOutline, SearchOutline } from '@ant-design/icons-angular/icons';

/**
 * An extension's type and status in the reader's language (QA SP-009).
 *
 * <p>The row stores the staff form's option value — the English name, "In
 * Progress" — which the page used to look up as if it were a code, so the
 * status stayed English in Arabic. The server now sends the resolved code
 * beside the stored value; the page names it from the code list, colours it
 * by code, and filters by code. A legacy value no lookup row matches keeps
 * the server's label, or failing that its own text.
 */
describe('ExtensionsComponent', () => {
    let fixture: ComponentFixture<ExtensionsComponent>;
    let component: ExtensionsComponent;

    const current: PortalExtension = {
        extensionId: 1009,
        extensionType: 'Financial Extension', extensionTypeCode: 'FINANCIAL', extensionTypeLabel: 'مالي',
        extensionStatus: 'In Progress', extensionStatusCode: 'IN_PROGRESS', extensionStatusLabel: 'قيد المعالجة',
        extensionReason: 'Budget Adjustment', extensionDecisionNo: 'EXT-2024-002'
    };
    const approved: PortalExtension = {
        extensionId: 1010,
        extensionType: 'Academic Extension', extensionTypeCode: 'ACADEMIC', extensionTypeLabel: 'اكاديمي',
        extensionStatus: 'Approved', extensionStatusCode: 'APPROVED', extensionStatusLabel: 'موافق عليه'
    };
    const legacy: PortalExtension = {
        extensionId: 1,
        extensionType: 'REGULAR', extensionTypeCode: null, extensionTypeLabel: 'REGULAR',
        extensionStatus: 'ACTIVE', extensionStatusCode: null, extensionStatusLabel: 'ACTIVE'
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [StudentPortalModule, RouterModule.forRoot([])],
            providers: [
                { provide: NZ_ICONS, useValue: [CalendarOutline, EyeOutline, SearchOutline] },
                { provide: StudentPortalApiService, useValue: { getExtensions: () => of([current, approved, legacy]) } },
                { provide: TranslationService, useValue: { currentLanguage$: of('ar'), initialized$: of(true), isReady: () => true, isRTL: () => true, translate: (k: string) => k, getCurrentLanguage: () => 'ar' } },
                {
                    provide: CodeListService,
                    useValue: codeListStub({
                        'extension-type': [
                            codeRow('ACADEMIC', { labelEn: 'Academic Extension', labelAr: 'اكاديمي' }),
                            codeRow('FINANCIAL', { labelEn: 'Financial Extension', labelAr: 'مالي' })
                        ],
                        'extension-status': [
                            codeRow('APPROVED', { labelEn: 'Approved', labelAr: 'موافق عليه' }),
                            codeRow('IN_PROGRESS', { labelEn: 'In Progress', labelAr: 'قيد المعالجة' })
                        ]
                    }, 'ar')
                }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(ExtensionsComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('names the type and the status from their code lists, by the resolved code', () => {
        expect(component.extensionTypeLabel(current)).toBe('مالي');
        expect(component.extensionStatusLabel(current)).toBe('قيد المعالجة');
        expect(component.extensionStatusLabel(approved)).toBe('موافق عليه');

        const text: string = fixture.nativeElement.textContent;
        expect(text).toContain('قيد المعالجة');
        expect(text).withContext('the stored English name is not what the student reads').not.toContain('In Progress');
    });

    it('keeps a legacy value the lookups do not know, as the server labelled it', () => {
        expect(component.statusKey(legacy)).toBe('ACTIVE');
        expect(component.extensionStatusLabel(legacy)).toBe('ACTIVE');
        expect(component.extensionTypeLabel(legacy)).toBe('REGULAR');
    });

    it('colours and filters by code, not by the stored text', () => {
        // "Approved" as stored never equalled 'APPROVED', so the tag was grey.
        expect(component.statusColor(approved)).toBe('green');
        expect(component.statusColor(current)).toBe('default');

        expect(component.statusOptions).toEqual(['IN_PROGRESS', 'APPROVED', 'ACTIVE']);
        expect(component.statusOptionLabel('APPROVED')).toBe('موافق عليه');

        component.statusFilter = 'APPROVED';
        expect(component.filteredList.map(r => r.extensionId)).toEqual([1010]);
    });

    it('searches the names the student sees', () => {
        component.search = 'مالي';
        expect(component.filteredList.map(r => r.extensionId)).toEqual([1009]);
    });
});
