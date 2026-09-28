import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from 'src/environments/environment';
import { UmsGroup, UmsRole } from '../api/ums';
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
export class UmsGroupService {
  private apiUrl = `${environment.apiUrl}/mohe/api/v1/ums/groups`;

  constructor(private http: HttpClient) { }

  getAllGroups(
    page = 0,
    size = 10,
    opts?: {
      sortBy?: string;
      sortDir?: string;
      keyword?: string;
      system?: boolean;
    }
  ): Observable<UmsPageResponse<UmsGroup>> {
    const params = buildUmsListParams({
      page,
      size,
      sortBy: opts?.sortBy ?? 'groupName',
      sortDir: opts?.sortDir ?? 'asc',
      keyword: opts?.keyword?.trim() || undefined,
      system: opts?.system
    });
    return this.http.get<UmsPageResponse<UmsGroup>>(`${this.apiUrl}/`, { params });
  }

  getAllActiveGroups(): Observable<UmsGroup[]> {
    return this.http.get<UmsGroup[]>(`${this.apiUrl}/all`);
  }

  searchGroups(keyword: string, page = 0, size = 10, opts?: { sortBy?: string; sortDir?: string; system?: boolean }): Observable<UmsPageResponse<UmsGroup>> {
    const params = buildUmsListParams({
      page,
      size,
      sortBy: opts?.sortBy ?? 'groupName',
      sortDir: opts?.sortDir ?? 'asc',
      keyword: keyword?.trim(),
      system: opts?.system
    });
    return this.http.get<UmsPageResponse<UmsGroup>>(`${this.apiUrl}/search`, { params });
  }

  getGroupById(id: number): Observable<UmsGroup> {
    return this.http.get<UmsGroup>(`${this.apiUrl}/${id}`);
  }

  createGroup(group: Partial<UmsGroup>): Observable<UmsGroup> {
    return this.http.post<UmsGroup>(`${this.apiUrl}/`, group, handled());
  }

  updateGroup(id: number, group: Partial<UmsGroup>): Observable<UmsGroup> {
    return this.http.put<UmsGroup>(`${this.apiUrl}/${id}`, group, handled());
  }

  deleteGroup(id: number): Observable<any> {
    return this.http.delete(`${this.apiUrl}/${id}`, handled());
  }

  getGroupRoles(groupId: number): Observable<UmsRole[]> {
    return this.http.get<UmsRole[]>(`${this.apiUrl}/${groupId}/roles`);
  }

  assignRoleToGroup(groupId: number, roleId: number): Observable<any> {
    return this.http.post(`${this.apiUrl}/${groupId}/roles/${roleId}`, {}, handled());
  }

  removeRoleFromGroup(groupId: number, roleId: number): Observable<any> {
    return this.http.delete(`${this.apiUrl}/${groupId}/roles/${roleId}`, handled());
  }
}
