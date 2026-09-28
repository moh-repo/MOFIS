import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { environment } from 'src/environments/environment';

/** A student as a dropdown option: an id and a name, and nothing else. */
export interface StudentOption {
    studentId: number;
    studentName: string;
}

/** What a `p-select` binds to: `{ id, label }`, the label being "id — name". */
export interface StudentPickerOption {
    id: number;
    label: string;
}

/**
 * The student dropdown that finance screens need.
 *
 * <p>Three screens name a student without being student screens: finance
 * recipients, payment authorisation and books allowance. All three used to ask
 * the student list — either `/studentdata/` (a bare array, so the caller's
 * `res.content` was always undefined and the dropdown was empty for everybody)
 * or `/studentdata/studentall` (correct, but gated on `STUDENT_PII`, which none
 * of the three finance roles hold). `/studentdata/options` exists for exactly
 * this: an id and a name, country-scoped, bounded, open to finance.
 *
 * <p>Only one page of students is ever held, so the search runs on the server —
 * filtering in the browser could only ever search the page in hand.
 */
@Injectable({ providedIn: 'root' })
export class StudentPickerService {

    private readonly url = `${environment.apiUrl}/mohe/api/v1/studentdata/options`;

    /** One page of options: by search term, or by the ids a list is already showing. */
    static readonly DEFAULT_LIMIT = 50;

    constructor(private http: HttpClient) {}

    search(term?: string, limit = StudentPickerService.DEFAULT_LIMIT): Observable<StudentOption[]> {
        return this.fetch({ search: term, limit });
    }

    /**
     * Resolves exactly these students.
     *
     * <p>A picker holds one page, so the students named by saved rows are almost
     * never among them; without this the table prints a bare id where a name
     * belongs. An id outside the caller's country scope simply does not come back.
     */
    byIds(ids: number[]): Observable<StudentOption[]> {
        const wanted = Array.from(new Set(ids.filter(id => !!id && id > 0)));
        return wanted.length === 0 ? of([]) : this.fetch({ ids: wanted });
    }

    private fetch(query: { search?: string; ids?: number[]; limit?: number }): Observable<StudentOption[]> {
        const params: string[] = [];
        if (query.search) params.push(`search=${encodeURIComponent(query.search)}`);
        if (query.ids?.length) params.push(`ids=${query.ids.join(',')}`);
        if (query.limit) params.push(`limit=${query.limit}`);
        return this.http.get<StudentOption[]>(`${this.url}?${params.join('&')}`).pipe(
            map(rows => Array.isArray(rows) ? rows : []),
            catchError(() => of([]))
        );
    }

    /**
     * Folds a page of options into the ones a picker is holding.
     *
     * <p>`keepIds` are the students the form or the table still refers to, and
     * they survive a search that excludes them — a `p-select` renders the label
     * of its selected value only while that value is one of its options.
     */
    merge(held: StudentOption[], incoming: StudentOption[], keepIds: (number | null | undefined)[] = []): StudentOption[] {
        const keep = new Set(keepIds.filter((id): id is number => !!id));
        const byId = new Map<number, StudentOption>();
        for (const s of held) {
            if (s?.studentId != null && keep.has(s.studentId)) byId.set(s.studentId, s);
        }
        for (const s of incoming) {
            if (s?.studentId != null) byId.set(s.studentId, s);
        }
        return Array.from(byId.values());
    }

    /** Adds every incoming option to the held ones, keeping all of them. */
    mergeAll(held: StudentOption[], incoming: StudentOption[]): StudentOption[] {
        const byId = new Map<number, StudentOption>();
        for (const s of [...held, ...incoming]) {
            if (s?.studentId != null) byId.set(s.studentId, s);
        }
        return Array.from(byId.values());
    }

    /** `{ id, label }` for a `p-select`, sorted by the label the user reads. */
    toOptions(students: StudentOption[]): StudentPickerOption[] {
        return students
            .map(s => ({
                id: s.studentId,
                label: [s.studentId?.toString(), s.studentName].filter(Boolean).join(' — ')
            }))
            .sort((a, b) => a.label.localeCompare(b.label));
    }
}
