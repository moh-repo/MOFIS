import { Component, OnInit } from '@angular/core';
import { Observable, forkJoin, of } from 'rxjs';
import { catchError, finalize, map, shareReplay, timeout } from 'rxjs/operators';
import { ToastService } from '../../demo/service/toast.service';
import {
  FieldChangeInput,
  StudentChangeRequest,
  StudentPortalApiService,
  PortalLookups,
  PortalLookupItem
} from '../student-portal-api.service';
import { TranslationService } from '../../demo/service/translation.service';
import { CodeListService } from '../../demo/service/code-list.service';
import { changeRequestDateText } from '../../demo/service/change-request-value.util';
import { pickLanguage } from '../../demo/pipes/in-language.pipe';
import { resolveChangeRequestFieldLabel } from './change-request-field-label';

interface EditableField {
  fieldName: string;
  /** The field's name in the reader's language, from the change-request-field list. */
  label: string;
  currentValue: string;
  newValue: string;
  selected: boolean;
  type: 'text' | 'select' | 'date';
  options?: PortalLookupItem[];
}

export interface RequestGroup {
  submissionId: string;
  date: string;
  items: StudentChangeRequest[];
  overallStatus: string;
}

@Component({
    selector: 'app-my-requests',
    templateUrl: './my-requests.component.html',
    styleUrls: ['./my-requests.component.scss'],
    standalone: false
})
export class MyRequestsComponent implements OnInit {

  requests: StudentChangeRequest[] = [];
  /** Pre-computed from requests: stable reference, never called inside template expressions. */
  groups: RequestGroup[] = [];
  loading = false;
  loadError = false;
  submitting = false;

  showSubmitDialog = false;
  editableFields: EditableField[] = [];
  studentDataLoading = false;

  private lookups: PortalLookups | null = null;
  /** CHANGE_REQUEST_FIELD.DATA_TYPE by field name; a DATE field gets a date input (review R2b). */
  private fieldTypes: Record<string, string> = {};

  /*
   * The reference lists and the field types, each fetched once when the page
   * opens and shared. The dialog waits for both: it used to build its rows from
   * whatever had arrived, so a student who opened it straight away got Gender
   * and Country as a raw id ("1") over a free-text box, and those rows stayed
   * that way until the dialog was opened again. Each settles even on failure
   * (null / {}), so the dialog never waits on them for ever.
   */
  private lookups$: Observable<PortalLookups | null> = of(null);
  private fieldTypes$: Observable<Record<string, string>> = of({});

  /** The lookup list a field's values come from (CHANGE_REQUEST_FIELD.LOOKUP_LIST), if any. */
  private lookupKeyFor(fieldName: string): keyof PortalLookups | null {
    return (this.codeLists.extra('change-request-field', fieldName) as keyof PortalLookups | null) ?? null;
  }

  constructor(
    private api: StudentPortalApiService,
    private message: ToastService,
    public translationService: TranslationService,
    private codeLists: CodeListService
  ) {}

  ngOnInit(): void {
    this.loadRequests();
    // Lookups load in the background, so they are usually there by the time the
    // dialog is opened. A failure is not fatal — the dialog falls back to
    // free-text fields — but it is said.
    this.lookups$ = this.api.getLookups().pipe(
      catchError(() => {
        this.message.warning(this.translationService.translate('STUDENT_PORTAL.LOAD_LOOKUPS_ERROR'));
        return of(null);
      }),
      shareReplay(1)
    );
    // Without it every field is a text box, as before; the server still reads a date typed there.
    this.fieldTypes$ = this.api.getAllowedChangeFields().pipe(
      timeout(8000),
      map(res => res?.types ?? {}),
      catchError(() => of({})),
      shareReplay(1)
    );
    // The submitted list names its values from the lookups too.
    this.lookups$.subscribe(l => { if (l) this.lookups = l; });
    this.fieldTypes$.subscribe(types => { this.fieldTypes = types; });
  }

  loadRequests(): void {
    this.loading = true;
    this.loadError = false;
    this.api.getMyChangeRequests()
      .pipe(
        timeout(15000),
        finalize(() => { this.loading = false; })
      )
      .subscribe({
        next: data => {
          this.requests = data;
          this.groups = this.buildGroups(data);
        },
        error: () => {
          this.loadError = true;
          this.requests = [];
          this.groups = [];
          this.message.error(this.translationService.translate('CHANGE_REQUEST.LOAD_ERROR'));
        }
      });
  }

  private buildGroups(requests: StudentChangeRequest[]): RequestGroup[] {
    const map = new Map<string, StudentChangeRequest[]>();
    for (const r of requests) {
      const list = map.get(r.submissionId) ?? [];
      list.push(r);
      map.set(r.submissionId, list);
    }
    return Array.from(map.entries()).map(([submissionId, items]) => ({
      submissionId,
      date: items[0]?.createdAt ?? '',
      items,
      overallStatus: this.deriveOverallStatus(items)
    }));
  }

  private deriveOverallStatus(items: StudentChangeRequest[]): string {
    const statuses = new Set(items.map(i => i.status));
    if (statuses.has('REJECTED'))                         return 'REJECTED';
    if (statuses.has('PENDING'))                          return 'PENDING';
    if ([...statuses].every(s => s === 'APPROVED'))       return 'APPROVED';
    if ([...statuses].every(s => s === 'MODIFIED'))       return 'MODIFIED';
    return 'MIXED';
  }

  overallStatusColor(status: string): string {
    const map: Record<string, string> = {
      PENDING:  'processing',
      APPROVED: 'success',
      REJECTED: 'error',
      MODIFIED: 'warning',
      MIXED:    'warning'
    };
    return map[status] ?? 'default';
  }

  statusColor(status: string): string {
    const map: Record<string, string> = {
      PENDING:  'processing',
      APPROVED: 'success',
      REJECTED: 'error',
      MODIFIED: 'warning'
    };
    return map[status] ?? 'default';
  }

  openSubmitDialog(): void {
    this.studentDataLoading = true;
    this.showSubmitDialog = true;
    this.editableFields = [];
    // Already-loaded lookups and types replay at once; otherwise the spinner
    // stays up until they arrive (see lookups$).
    forkJoin({
      data: this.api.getMyData().pipe(
        timeout(12000),
        // The dialog still opens with blank current values; the student is told why.
        catchError(() => {
          this.message.warning(this.translationService.translate('STUDENT_PORTAL.LOAD_MY_DATA_ERROR'));
          return of({ student: null, finance: null });
        })
      ),
      lookups: this.lookups$,
      types: this.fieldTypes$
    })
      .pipe(finalize(() => { this.studentDataLoading = false; }))
      .subscribe(({ data, lookups, types }) => {
        if (lookups) this.lookups = lookups;
        this.fieldTypes = types;
        const studentData: Record<string, any> = (data.student as Record<string, any>) ?? {};
        // The editable fields are the change-request-field list's active rows.
        this.editableFields = this.codeLists.active('change-request-field').map(field => {
          const fieldName = field.value;
          const rawVal = studentData[fieldName];
          const lookupKey = this.lookupKeyFor(fieldName);
          const opts: PortalLookupItem[] | undefined = lookupKey && this.lookups
            ? (this.lookups[lookupKey] as PortalLookupItem[])
            : undefined;

          const isDate = !opts?.length && this.fieldTypes[fieldName] === 'DATE';
          let currentValue = rawVal != null ? String(rawVal) : '';
          if (isDate) currentValue = changeRequestDateText(currentValue);
          if (opts?.length && rawVal != null) {
            const found = opts.find(o => o.id === Number(rawVal));
            if (found) currentValue = pickLanguage(found, 'name', this.translationService.getCurrentLanguage());
          }

          return {
            fieldName,
            label: this.codeLists.label('change-request-field', fieldName),
            currentValue,
            newValue: '',
            selected: false,
            type: (opts?.length ? 'select' : isDate ? 'date' : 'text') as EditableField['type'],
            options: opts
          };
        });
      });
  }

  get selectedFields(): EditableField[] {
    return this.editableFields.filter(f => f.selected);
  }

  submitRequest(): void {
    const toSubmit = this.selectedFields.filter(f => f.newValue?.trim?.());
    if (!toSubmit.length) {
      this.message.warning(this.translationService.translate('CHANGE_REQUEST.NO_CHANGES'));
      return;
    }

    const changes: FieldChangeInput[] = toSubmit.map(f => ({
      fieldName: f.fieldName,
      fieldLabel: f.label,
      newValue: f.newValue.trim()
    }));

    this.submitting = true;
    this.api.submitChangeRequest(changes)
      .pipe(finalize(() => { this.submitting = false; }))
      .subscribe({
        next: () => {
          this.showSubmitDialog = false;
          this.message.success(this.translationService.translate('CHANGE_REQUEST.SUBMIT_SUCCESS'));
          this.loadRequests();
        },
        error: (err) => {
          this.message.error(err.error?.error || this.translationService.translate('CHANGE_REQUEST.SUBMIT_ERROR'));
        }
      });
  }

  /**
   * The field's name in the reader's language, from the change-request-field code
   * list the dialog uses. The stored label was written in the language the request
   * was made in (QA SP-011); an older request's code is matched back to its row
   * through that label (live M6), and the label stays the fallback for a field the
   * list lost. See change-request-field-label.ts.
   */
  fieldLabel(fieldName: string, stored?: string | null): string {
    return resolveChangeRequestFieldLabel(this.codeLists, fieldName, stored);
  }

  resolveValue(fieldName: string, rawValue?: string | null): string {
    if (rawValue == null || rawValue === '') return '—';
    const key = this.lookupKeyFor(fieldName);
    if (!key || !this.lookups?.[key]?.length) return changeRequestDateText(rawValue);
    const found = (this.lookups[key] as PortalLookupItem[]).find(o => o.id === Number(rawValue));
    return found ? pickLanguage(found, 'name', this.translationService.getCurrentLanguage()) : rawValue;
  }

  trackGroup(_: number, group: RequestGroup): string {
    return group.submissionId;
  }

  cancelRequest(id: number): void {
    this.api.cancelChangeRequest(id).subscribe({
      next: () => {
        this.message.success(this.translationService.translate('CHANGE_REQUEST.CANCEL_SUCCESS'));
        this.loadRequests();
      },
      error: () => {
        this.message.error(this.translationService.translate('CHANGE_REQUEST.CANCEL_ERROR'));
      }
    });
  }
}
