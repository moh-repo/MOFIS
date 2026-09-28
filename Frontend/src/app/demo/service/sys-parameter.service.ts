import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiUrlService } from './api-url.service';

export type SysParameterValueType = 'INT' | 'DECIMAL' | 'TEXT' | 'BOOL';

/** One row of MOHECORE.SYS_PARAMETER as `/api/v1/sys-parameters` serves it. */
export interface SysParameter {
    key: string;
    value: string;
    valueType: SysParameterValueType;
    nameAr: string | null;
    nameEn: string | null;
    description: string | null;
    updatedBy: string | null;
    updatedAt: string | null;
}

/** System parameters: the windows, thresholds and floors the server reads at request time. */
@Injectable({ providedIn: 'root' })
export class SysParameterService {

    constructor(private http: HttpClient, private apiUrl: ApiUrlService) { }

    getAll(): Observable<SysParameter[]> {
        return this.http.get<SysParameter[]>(this.apiUrl.apiPath('/api/v1/sys-parameters'));
    }

    update(key: string, value: string): Observable<SysParameter> {
        return this.http.put<SysParameter>(this.apiUrl.apiPath(`/api/v1/sys-parameters/${encodeURIComponent(key)}`), { value });
    }
}
