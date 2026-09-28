import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { NzModalService } from 'ng-zorro-antd/modal';
import { of } from 'rxjs';

import { BackupRestoreComponent } from './backup-restore/backup-restore.component';
import { DataImportComponent } from './data-import/data-import.component';
import { TranslationService } from '../../service/translation.service';
import { ToastService } from '../../service/toast.service';
import { MessageService } from 'primeng/api';
import { NZ_ICONS } from 'ng-zorro-antd/icon';
import {
    ClearOutline, CloseCircleOutline, DatabaseOutline, DeleteOutline, DownloadOutline, DragOutline,
    EditOutline, ExclamationCircleOutline, EyeOutline, FileExcelOutline, ImportOutline, InfoCircleOutline,
    LinkOutline, NodeIndexOutline, ReloadOutline, SearchOutline, TableOutline, ThunderboltOutline,
    UndoOutline, UploadOutline
} from '@ant-design/icons-angular/icons';

/**
 * The headings on the two System-tools pages.
 *
 * <p>The other four screens under `/system-admin` lead their heading with an
 * icon; these two are ng-zorro cards whose title was a plain string, so they had
 * none — and they are outside `page-headings.spec.ts`, which measures a `.card`
 * with an `h1..h5` in a flex row and finds neither here.
 *
 * <p>The titles are `ng-template`s now, which is what lets a card's heading hold
 * anything but text. Asserted on the rendered card head rather than on the
 * template, because `nzTitle` accepting a template is the whole of the change.
 */

const translation = {
    currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
    isRTL: () => false, getCurrentLanguage: () => 'en',
    translate: (key: string) => key.split('.').pop()!.replace(/_/g, ' ')
};

const build = async (component: any): Promise<any> => {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
        imports: [component],
        providers: [
            provideNoopAnimations(),
            MessageService,
            // The icons these two pages render. Without them the icon directive
            // fetches each SVG over HttpClient, which a stubbed client cannot
            // answer, and the fixture never settles.
            {
                provide: NZ_ICONS,
                useValue: [
                    ClearOutline, CloseCircleOutline, DatabaseOutline, DeleteOutline, DownloadOutline,
                    DragOutline, EditOutline, ExclamationCircleOutline, EyeOutline, FileExcelOutline,
                    ImportOutline, InfoCircleOutline, LinkOutline, NodeIndexOutline, ReloadOutline,
                    SearchOutline, TableOutline, ThunderboltOutline, UndoOutline, UploadOutline
                ]
            },
            { provide: HttpClient, useValue: { get: () => of([]), post: () => of({}), put: () => of({}), delete: () => of({}) } },
            { provide: ToastService, useValue: new Proxy({} as any, { get: () => () => undefined }) },
            { provide: NzModalService, useValue: { confirm: () => undefined, create: () => undefined } },
            { provide: TranslationService, useValue: translation }
        ]
    }).compileComponents();

    const fixture = TestBed.createComponent(component);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
};

([['backup and restore', BackupRestoreComponent],
  ['data import', DataImportComponent]] as Array<[string, any]>)
    .forEach(([name, component]) => {

        it(`${name} leads every card heading with an icon`, async () => {
            const fixture = await build(component);

            const heads = Array.from(
                fixture.nativeElement.querySelectorAll('.ant-card-head-title')) as HTMLElement[];
            expect(heads.length).withContext(`${name} renders no card heading`).toBeGreaterThan(0);

            const bare = heads
                .filter(h => h.querySelector('[nz-icon], .anticon') === null)
                .map(h => (h.textContent ?? '').trim().slice(0, 40));
            expect(bare)
                .withContext(`${name} has a card heading with no icon in front of it`)
                .toEqual([]);
        });
    });
