import { Component, OnInit, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpParams } from '@angular/common/http';
import { NzTableModule } from 'ng-zorro-antd/table';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzCardModule } from 'ng-zorro-antd/card';
import { ToastService } from '../../service/toast.service';
import { HasPermissionDirective } from '../../directives/has-permission.directive';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzDividerModule } from 'ng-zorro-antd/divider';
import { NzModalModule, NzModalService } from 'ng-zorro-antd/modal';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzSpinModule } from 'ng-zorro-antd/spin';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { PanelModule } from 'primeng/panel';
import { environment } from 'src/environments/environment';
import { TranslationService } from '../../service/translation.service';
import { LookupService } from '../../service/lookup.service';
import { CodeListService } from '../../service/code-list.service';
import { UmsAuthService } from '../../service/ums-auth.service';
import { UmsPermissionService } from '../../service/ums-permission.service';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { changesOnly } from '../../service/reload-streams.util';

const BASE = `${environment.apiUrl}/mohe/api/v1/wafar-reconciliation`;

interface Reconciliation {
    reconId: number;
    studentId: number;
    reconDate: string;
    moheAmount: number;
    wafarAmount: number;
    delta: number;
    reconStatus: string;
    notes: string;
    createdBy: string;
    resolvedBy: string;
    resolvedDate: string;
}

interface Summary { MATCHED: number; DISCREPANCY: number; RESOLVED: number; }

@Component({
    selector: 'app-wafar-reconciliation',
    imports: [
        CommonModule, FormsModule,
        NzTableModule, NzButtonModule, NzCardModule, NzTagModule,
        NzSelectModule, NzDividerModule,
        NzModalModule, NzInputModule, NzSpinModule, NzEmptyModule,
        PanelModule,
        TranslatePipe,
        HasPermissionDirective
    ],
    providers: [NzModalService],
    templateUrl: './wafar-reconciliation.component.html',
    styleUrls: ['./wafar-reconciliation.component.scss']
})
export class WafarReconciliationComponent implements OnInit {
    private readonly codeLists = inject(CodeListService);
    private readonly authService = inject(UmsAuthService);
    private readonly permissions = inject(UmsPermissionService);

    /**
     * Whether the Run button is drawn. UmsPermissionService hides every WAFAR write
     * from ministry-level accounts (N-04), which is right for recording a spend but
     * also hid the button from the super admin, who may run the comparison under the
     * D-13(b) hatch (live QA M2, 23 Sep 2026). The server decides either way.
     */
    get canRun(): boolean {
        return this.authService.isSuperAdmin() || this.permissions.hasPermission('WAFAR.EDIT');
    }

    rows: Reconciliation[] = [];
    totalItems = 0;
    page = 0;
    pageSize = 20;
    loading = false;
    running = false;
    filterStatus: string | null = null;

    summary: Summary = { MATCHED: 0, DISCREPANCY: 0, RESOLVED: 0 };
    /** The figures could not be read: the band says so instead of showing zeros (QA B9). */
    summaryFailed = false;

    resolveVisible = false;
    resolveId: number | null = null;
    resolveNotes = '';
    resolving = false;

    statusOptions: { label: string; value: string | null }[] = [];

    // currentLanguage$ never completes, so the subscription is tied to the component lifetime.
    private readonly destroyRef = inject(DestroyRef);

    constructor(
        private http: HttpClient,
        private msg: ToastService,
        private modal: NzModalService,
        private translationService: TranslationService,
        private lookupService: LookupService
    ) {}

    ngOnInit(): void {
        this.loadStatusOptions();
        this.translationService.currentLanguage$.pipe(changesOnly(), takeUntilDestroyed(this.destroyRef)).subscribe(() => this.loadStatusOptions());
        this.loadSummary();
        this.loadRows();
    }

    private loadStatusOptions(): void {
        this.lookupService.getWafarReconciliationStatuses().subscribe(statuses => {
            this.statusOptions = [
                { label: this.translationService.translate('COMMON.ALL'), value: null },
                ...statuses
            ];
        });
    }

    /**
     * The API answers `matched` / `discrepancy` / `resolved`. The page read the
     * upper-case names, found nothing and showed 0 / 0 / 0 while the list below held
     * 21 matched and 920 discrepancies (QA FIN-002).
     */
    loadSummary(): void {
        this.http.get<Record<string, number>>(`${BASE}/summary`).subscribe({
            next: (s) => {
                this.summary = {
                    MATCHED: s?.['matched'] ?? 0,
                    DISCREPANCY: s?.['discrepancy'] ?? 0,
                    RESOLVED: s?.['resolved'] ?? 0
                };
                this.summaryFailed = false;
            },
            error: () => { this.summaryFailed = true; }
        });
    }

    /** Column the server orders by, and its direction; null is the default order. */
    sortField: string | null = null;
    sortOrder: 'ascend' | 'descend' | null = null;

    /** A header click. One column at a time, ordered on the server across every page. */
    onSort(field: string, order: string | null): void {
        this.sortField = order ? field : null;
        this.sortOrder = (order as 'ascend' | 'descend') ?? null;
        this.page = 0;
        this.loadRows();
    }

    sortOrderOf(field: string): 'ascend' | 'descend' | null {
        return this.sortField === field ? this.sortOrder : null;
    }

    loadRows(): void {
        this.loading = true;
        let params = new HttpParams()
            .set('page', this.page)
            .set('size', this.pageSize);
        if (this.filterStatus) params = params.set('status', this.filterStatus);
        if (this.sortField && this.sortOrder) {
            params = params.set('sortBy', this.sortField)
                .set('sortDir', this.sortOrder === 'descend' ? 'desc' : 'asc');
        }
        this.http.get<any>(`${BASE}`, { params }).subscribe({
            next: (res) => {
                this.rows = res.content ?? res;
                this.totalItems = res.totalElements ?? this.rows.length;
                this.loading = false;
            },
            error: () => { this.loading = false; }
        });
    }

    runReconciliation(): void {
        this.modal.confirm({
            nzTitle: this.translationService.translate('WAFAR_RECONCILIATION.RUN_CONFIRM_TITLE'),
            nzContent: this.translationService.translate('WAFAR_RECONCILIATION.RUN_CONFIRM_BODY'),
            nzOnOk: () => {
                this.running = true;
                this.http.post<any>(`${BASE}/run`, {}).subscribe({
                    next: (res) => {
                        this.running = false;
                        this.msg.success(`${this.translationService.translate('WAFAR_RECONCILIATION.RUN_DONE')}: ${res.discrepanciesFound} ${this.translationService.translate('WAFAR_RECONCILIATION.DISCREPANCIES')}`);
                        this.loadSummary();
                        this.loadRows();
                    },
                    error: () => {
                        this.running = false;
                        this.msg.error(this.translationService.translate('COMMON.ERROR'));
                    }
                });
            }
        });
    }

    openResolve(row: Reconciliation): void {
        this.resolveId = row.reconId;
        this.resolveNotes = row.notes || '';
        this.resolveVisible = true;
    }

    submitResolve(): void {
        if (!this.resolveId) return;
        this.resolving = true;
        this.http.put<Reconciliation>(`${BASE}/${this.resolveId}/resolve`, null, {
            params: { notes: this.resolveNotes }
        }).subscribe({
            next: () => {
                this.resolving = false;
                this.resolveVisible = false;
                this.msg.success(this.translationService.translate('WAFAR_RECONCILIATION.RESOLVED_OK'));
                this.loadSummary();
                this.loadRows();
            },
            error: () => {
                this.resolving = false;
                this.msg.error(this.translationService.translate('COMMON.ERROR'));
            }
        });
    }

    onPageChange(p: number): void { this.page = p - 1; this.loadRows(); }
    onFilterChange(): void { this.page = 0; this.loadRows(); }

    /** ng-zorro preset for the status's wafar-reconciliation-status row. */
    statusColor(status: string): string {
        return this.codeLists.nzColor('wafar-reconciliation-status', status);
    }

    statusLabel(status: string): string {
        return this.codeLists.label('wafar-reconciliation-status', status);
    }
}
