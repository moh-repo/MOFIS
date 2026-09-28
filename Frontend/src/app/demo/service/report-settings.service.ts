import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiUrlService } from './api-url.service';

export interface ReportTypeSettingsDto {
    id?: number;
    reportTypeCode: string;
    reportTypeNameAr?: string | null;
    reportTypeNameEn?: string | null;
    isActive?: boolean;

    title01Ar?: string | null; title01En?: string | null;
    title02Ar?: string | null; title02En?: string | null;
    title03Ar?: string | null; title03En?: string | null;
    title04Ar?: string | null; title04En?: string | null;
    title05Ar?: string | null; title05En?: string | null;

    footerTextAr?: string | null;
    footerTextEn?: string | null;

    signatoryCount?: number;

    signatoryName01Ar?: string | null; signatoryName01En?: string | null;
    signatoryPos01Ar?: string | null;  signatoryPos01En?: string | null;

    signatoryName02Ar?: string | null; signatoryName02En?: string | null;
    signatoryPos02Ar?: string | null;  signatoryPos02En?: string | null;

    signatoryName03Ar?: string | null; signatoryName03En?: string | null;
    signatoryPos03Ar?: string | null;  signatoryPos03En?: string | null;

    signatoryName04Ar?: string | null; signatoryName04En?: string | null;
    signatoryPos04Ar?: string | null;  signatoryPos04En?: string | null;

    signatoryName05Ar?: string | null; signatoryName05En?: string | null;
    signatoryPos05Ar?: string | null;  signatoryPos05En?: string | null;
}

/** Helper: extracts the active title slots (non-empty AR or EN) in order. */
export function getActiveTitles(s: ReportTypeSettingsDto, isRtl: boolean): string[] {
    const slots: [string | null | undefined, string | null | undefined][] = [
        [s.title01Ar, s.title01En],
        [s.title02Ar, s.title02En],
        [s.title03Ar, s.title03En],
        [s.title04Ar, s.title04En],
        [s.title05Ar, s.title05En],
    ];
    return slots
        .filter(([ar, en]) => (ar && ar.trim()) || (en && en.trim()))
        .map(([ar, en]) => (isRtl ? ar || en || '' : en || ar || ''));
}

/** Helper: extracts the active signatory slots (first signatoryCount). */
export function getActiveSignatories(s: ReportTypeSettingsDto, isRtl: boolean): { name: string; position: string }[] {
    const count = s.signatoryCount ?? 0;
    const all = [
        { nameAr: s.signatoryName01Ar, nameEn: s.signatoryName01En, posAr: s.signatoryPos01Ar, posEn: s.signatoryPos01En },
        { nameAr: s.signatoryName02Ar, nameEn: s.signatoryName02En, posAr: s.signatoryPos02Ar, posEn: s.signatoryPos02En },
        { nameAr: s.signatoryName03Ar, nameEn: s.signatoryName03En, posAr: s.signatoryPos03Ar, posEn: s.signatoryPos03En },
        { nameAr: s.signatoryName04Ar, nameEn: s.signatoryName04En, posAr: s.signatoryPos04Ar, posEn: s.signatoryPos04En },
        { nameAr: s.signatoryName05Ar, nameEn: s.signatoryName05En, posAr: s.signatoryPos05Ar, posEn: s.signatoryPos05En },
    ];
    return all.slice(0, count).map(r => ({
        name:     isRtl ? (r.nameAr || r.nameEn || '') : (r.nameEn || r.nameAr || ''),
        position: isRtl ? (r.posAr  || r.posEn  || '') : (r.posEn  || r.posAr  || ''),
    }));
}

@Injectable({ providedIn: 'root' })
export class ReportSettingsService {

    private readonly base = '/api/v1/report-settings';

    constructor(
        private http: HttpClient,
        private apiUrl: ApiUrlService
    ) {}

    getAll(): Observable<ReportTypeSettingsDto[]> {
        return this.http.get<ReportTypeSettingsDto[]>(this.apiUrl.apiPath(this.base));
    }

    getByCode(code: string): Observable<ReportTypeSettingsDto> {
        return this.http.get<ReportTypeSettingsDto>(this.apiUrl.apiPath(`${this.base}/${code}`));
    }

    /** Report codes the system prints that have no settings yet (DEC-22). */
    catalogue(): Observable<string[]> {
        return this.http.get<string[]>(this.apiUrl.apiPath(`${this.base}/catalogue`));
    }

    create(dto: ReportTypeSettingsDto): Observable<ReportTypeSettingsDto> {
        return this.http.post<ReportTypeSettingsDto>(this.apiUrl.apiPath(this.base), dto);
    }

    update(id: number, dto: ReportTypeSettingsDto): Observable<ReportTypeSettingsDto> {
        return this.http.put<ReportTypeSettingsDto>(this.apiUrl.apiPath(`${this.base}/${id}`), dto);
    }

    delete(id: number): Observable<void> {
        return this.http.delete<void>(this.apiUrl.apiPath(`${this.base}/${id}`));
    }
}
