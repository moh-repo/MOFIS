import { Component, DestroyRef, inject, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MessageService } from 'primeng/api';
import { Table } from 'primeng/table';
import { TranslationService } from '../../../service/translation.service';
import { environment } from '../../../../../environments/environment';

const BASE = `${environment.apiUrl}/mohe/api/v1/student-admin/permissions`;

@Component({
    selector: 'app-student-permissions',
    templateUrl: './student-permissions.component.html',
    providers: [MessageService],
    standalone: false
})
export class StudentPermissionsComponent implements OnInit {

  permissions: any[] = [];

  /** The row the detail dialog is showing; the catalogue itself is read-only. */
  selectedPermission: any = null;
  showViewDialog = false;
  loading = false;

  // Filters. The list is held whole, so the table narrows itself.
  filterSearch = '';
  filterActive: boolean | null = null;
  activeOptions: { label: string; value: boolean | null }[] = [];

  private readonly destroyRef = inject(DestroyRef);

  constructor(
    private http: HttpClient,
    private messageService: MessageService,
    public t: TranslationService
  ) {}

  ngOnInit(): void {
    this.buildOptions();
    this.load();
    this.t.currentLanguage$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.buildOptions());
  }

  private buildOptions(): void {
    this.activeOptions = [
      { label: this.t.translate('COMMON.ALL'), value: null },
      { label: this.t.translate('COMMON.ACTIVE'), value: true },
      { label: this.t.translate('COMMON.INACTIVE'), value: false }
    ];
  }

  load(): void {
    this.loading = true;
    this.http.get<any[]>(BASE).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: d => { this.permissions = d; this.loading = false; },
      error: () => { this.loading = false; this.messageService.add({ severity: 'error', summary: this.t.translate('COMMON.ERROR'), detail: this.t.translate('STUDENT_ADMIN.PERMISSIONS.MESSAGES.LOAD_ERROR') }); }
    });
  }

  get hasFilters(): boolean {
    return !!this.filterSearch || this.filterActive !== null;
  }

  clearFilters(dt: Table): void {
    this.filterSearch = '';
    this.filterActive = null;
    dt.filterGlobal('', 'contains');
    dt.filter(null, 'isActive', 'equals');
  }

  openView(permission: any): void {
    this.selectedPermission = permission;
    this.showViewDialog = true;
  }
}
