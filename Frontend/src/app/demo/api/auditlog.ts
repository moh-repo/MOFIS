import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

// Legacy interface for backward compatibility (used by audit log page table)
export interface LegacyAuditLog {
  id: number;
  tableName: string;
  recordId: string;
  action: string;
  oldValue: string;
  newValue: string;
  changedBy: string;
  changedAt: string;
  changedColumns?: string;
  ipAddress?: string;
  sessionId?: string;
}

export interface AuditLog {
  logId: number;
  tableName: string;
  schemaName: string;
  recordId: string;
  operation: string;
  oldValues: string;
  newValues: string;
  changedColumns: string;
  changedBy: string;
  changedAt: string;
  ipAddress: string;
  sessionId: string;
  applicationName: string;
}

export interface AuditLogResponse {
  content: AuditLog[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
  pageSize: number;
}

export interface AuditLogFilters {
  tableName?: string;
  recordId?: string;
  operation?: string;
  changedBy?: string;
  startDate?: Date;
  endDate?: Date;
  page?: number;
  size?: number;
  /** A column of MoheAuditLogController.SORTABLE; anything else is a 400. */
  sortBy?: string;
  sortDir?: 'asc' | 'desc';
}

@Injectable({
  providedIn: 'root'
})
export class AuditLogService {
  private apiUrl = `${environment.apiUrl}/mohe/api/audit-logs`;

  constructor(private http: HttpClient) {}

  getAuditLogs(filters?: AuditLogFilters): Observable<AuditLogResponse> {
    let params = new HttpParams();
    
    if (filters) {
      if (filters.tableName) params = params.set('tableName', filters.tableName);
      if (filters.recordId) params = params.set('recordId', filters.recordId);
      if (filters.operation) params = params.set('operation', filters.operation);
      if (filters.changedBy) params = params.set('changedBy', filters.changedBy);
      // Local wall-clock, not toISOString(): the controller binds these to a
      // LocalDateTime, which drops the offset a UTC string carries, so a day
      // that starts at local midnight arrived as 21:00 the day before.
      if (filters.startDate) params = params.set('startDate', localDateTime(filters.startDate));
      if (filters.endDate) params = params.set('endDate', localDateTime(filters.endDate));
      if (filters.page !== undefined) params = params.set('page', filters.page.toString());
      if (filters.size !== undefined) params = params.set('size', filters.size.toString());
      // The order is the server's to apply: one page of fifty rows cannot be
      // sorted in the browser without sorting only that page.
      if (filters.sortBy) params = params.set('sortBy', filters.sortBy);
      if (filters.sortDir) params = params.set('sortDir', filters.sortDir);
    }

    return this.http.get<AuditLogResponse>(this.apiUrl, { params });
  }

  getAuditLogById(id: number): Observable<AuditLog> {
    return this.http.get<AuditLog>(`${this.apiUrl}/${id}`);
  }

  getAuditLogsByTable(tableName: string): Observable<LegacyAuditLog[]> {
    return new Observable(observer => {
      this.http.get<AuditLog[]>(`${this.apiUrl}/table/${tableName}`).subscribe(logs => {
        const legacyLogs: LegacyAuditLog[] = logs.map(log => toLegacyLog(log));
        observer.next(legacyLogs);
        observer.complete();
      }, error => {
        observer.error(error);
      });
    });
  }

  getAuditLogsByRecordId(recordId: string): Observable<AuditLog[]> {
    return this.http.get<AuditLog[]>(`${this.apiUrl}/record/${recordId}`);
  }

  getAuditLogsByOperation(operation: string): Observable<AuditLog[]> {
    return this.http.get<AuditLog[]>(`${this.apiUrl}/operation/${operation}`);
  }

  getAuditLogsByUser(changedBy: string): Observable<LegacyAuditLog[]> {
    return new Observable(observer => {
      this.http.get<AuditLog[]>(`${this.apiUrl}/user/${changedBy}`).subscribe(logs => {
        const legacyLogs: LegacyAuditLog[] = logs.map(log => toLegacyLog(log));
        observer.next(legacyLogs);
        observer.complete();
      }, error => {
        observer.error(error);
      });
    });
  }

  getDistinctTableNames(): Observable<string[]> {
    return this.http.get<string[]>(`${this.apiUrl}/metadata/tables`);
  }

  getDistinctUsers(): Observable<string[]> {
    return this.http.get<string[]>(`${this.apiUrl}/metadata/users`);
  }

  // Legacy methods for backward compatibility
  getAllAuditLogs(): Observable<LegacyAuditLog[]> {
    return new Observable(observer => {
      this.getAuditLogs({ page: 0, size: 1000 }).subscribe(response => {
        const legacyLogs: LegacyAuditLog[] = response.content.map(log => toLegacyLog(log));
        observer.next(legacyLogs);
        observer.complete();
      }, error => {
        observer.error(error);
      });
    });
  }
}

/** `yyyy-MM-ddTHH:mm:ss` in the browser's own zone — what an ISO LocalDateTime is. */
export function localDateTime(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}

function toLegacyLog(log: AuditLog): LegacyAuditLog {
  return {
    id: log.logId,
    tableName: log.tableName,
    recordId: log.recordId,
    action: log.operation,
    oldValue: log.oldValues ?? '',
    newValue: log.newValues ?? '',
    changedBy: log.changedBy ?? '',
    changedAt: log.changedAt,
    changedColumns: log.changedColumns ?? '',
    ipAddress: log.ipAddress ?? '',
    sessionId: log.sessionId ?? ''
  };
}
