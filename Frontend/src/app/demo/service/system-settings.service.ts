import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiUrlService } from './api-url.service';

export interface SysSettingsDto {
    sysNameAr: string | null;
    sysNameEn: string | null;
    sysLogoPath: string | null;
    sysLogoFilename: string | null;
}

@Injectable({ providedIn: 'root' })
export class SystemSettingsService {

    private readonly base = '/api/v1/sys-settings';

    constructor(
        private http: HttpClient,
        private apiUrl: ApiUrlService
    ) {}

    getSettings(): Observable<SysSettingsDto> {
        return this.http.get<SysSettingsDto>(this.apiUrl.apiPath(this.base));
    }

    updateSettings(dto: SysSettingsDto): Observable<SysSettingsDto> {
        return this.http.put<SysSettingsDto>(this.apiUrl.apiPath(this.base), dto);
    }

    uploadLogo(file: File): Observable<SysSettingsDto> {
        const form = new FormData();
        form.append('file', file);
        return this.http.post<SysSettingsDto>(this.apiUrl.apiPath(`${this.base}/logo`), form);
    }

    /** Full URL to the logo image endpoint (public, no auth required). */
    getLogoUrl(): string {
        return this.apiUrl.apiPath(`${this.base}/logo`);
    }
}
