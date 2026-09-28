import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from 'src/environments/environment';
import { UmsRole, UmsPermission } from '../api/ums';
import { buildUmsListParams, UmsPageResponse } from './ums-list-params';
import { silentErrors } from '../interceptors/http-context.tokens';

/**
 * Options for a write whose failure the calling page reports itself. Every page that
 * writes through this service shows its own error, so the interceptor's toast was a
 * second one for the same failure (live QA #18).
 */
const handled = () => ({ context: silentErrors() });

@Injectable({
  providedIn: 'root'
})
export class UmsRoleService {
  private apiUrl = `${environment.apiUrl}/mohe/api/v1/ums/roles`;

  constructor(private http: HttpClient) { }

  getAllRoles(
    page = 0,
    size = 10,
    opts?: {
      sortBy?: string;
      sortDir?: string;
      keyword?: string;
      system?: boolean;
    }
  ): Observable<UmsPageResponse<UmsRole>> {
    const params = buildUmsListParams({
      page,
      size,
      sortBy: opts?.sortBy ?? 'roleName',
      sortDir: opts?.sortDir ?? 'asc',
      keyword: opts?.keyword?.trim() || undefined,
      system: opts?.system
    });
    return this.http.get<UmsPageResponse<UmsRole>>(`${this.apiUrl}/`, { params });
  }

  getAllActiveRoles(): Observable<UmsRole[]> {
    return this.http.get<UmsRole[]>(`${this.apiUrl}/all`);
  }

  searchRoles(keyword: string, page = 0, size = 10, opts?: { sortBy?: string; sortDir?: string; system?: boolean }): Observable<UmsPageResponse<UmsRole>> {
    const params = buildUmsListParams({
      page,
      size,
      sortBy: opts?.sortBy ?? 'roleName',
      sortDir: opts?.sortDir ?? 'asc',
      keyword: keyword?.trim(),
      system: opts?.system
    });
    return this.http.get<UmsPageResponse<UmsRole>>(`${this.apiUrl}/search`, { params });
  }

  getRoleById(id: number): Observable<UmsRole> {
    return this.http.get<UmsRole>(`${this.apiUrl}/${id}`);
  }

  createRole(role: Partial<UmsRole>): Observable<UmsRole> {
    return this.http.post<UmsRole>(`${this.apiUrl}/`, role, handled());
  }

  updateRole(id: number, role: Partial<UmsRole>): Observable<UmsRole> {
    return this.http.put<UmsRole>(`${this.apiUrl}/${id}`, role, handled());
  }

  deleteRole(id: number): Observable<any> {
    return this.http.delete(`${this.apiUrl}/${id}`, handled());
  }

  getRolePermissions(roleId: number): Observable<UmsPermission[]> {
    return this.http.get<UmsPermission[]>(`${this.apiUrl}/${roleId}/permissions`);
  }

  assignPermissionToRole(roleId: number, permissionId: number): Observable<any> {
    return this.http.post(`${this.apiUrl}/${roleId}/permissions/${permissionId}`, {}, handled());
  }

  removePermissionFromRole(roleId: number, permissionId: number): Observable<any> {
    return this.http.delete(`${this.apiUrl}/${roleId}/permissions/${permissionId}`, handled());
  }

  /** An empty list is refused unless `clearAll` says the role is meant to lose everything. */
  setRolePermissions(roleId: number, permissionIds: number[], clearAll = false): Observable<any> {
    const options = clearAll ? { params: { clearAll: 'true' }, ...handled() } : handled();
    return this.http.put(`${this.apiUrl}/${roleId}/permissions`, permissionIds, options);
  }
}
