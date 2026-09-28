import { Injectable } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from 'src/environments/environment';
import { UmsUser, UmsUserSummary, UmsUserOption, UmsMfaStatus, UmsGroup, UmsRole, UmsUserCountry, UmsPermission, UmsDirectGrant, EffectivePermissionReport, EffectivePermissionDiff, DirectGrantException, SeparationOfDutiesReport, AccessReview, AccessReviewItem } from '../api/ums';
import { HttpParams } from '@angular/common/http';
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
export class UmsUserService {
  private apiUrl = `${environment.apiUrl}/mohe/api/v1/ums/users`;

  constructor(private http: HttpClient) { }

  // ========== User CRUD ==========

  getAllUsers(
    page = 0,
    size = 10,
    opts?: {
      sortBy?: string;
      sortDir?: string;
      keyword?: string;
      status?: string;
      privilege?: string;
    }
  ): Observable<UmsPageResponse<UmsUserSummary>> {
    const sortBy = opts?.sortBy ?? 'userName';
    const sortDir = opts?.sortDir ?? 'asc';
    const params = buildUmsListParams({
      page,
      size,
      sortBy,
      sortDir,
      keyword: opts?.keyword?.trim() || undefined,
      status: opts?.status?.trim() || undefined,
      privilege: opts?.privilege?.trim() || undefined
    });
    return this.http.get<UmsPageResponse<UmsUserSummary>>(`${this.apiUrl}/`, { params });
  }

  searchUsers(keyword: string, page = 0, size = 10, opts?: { sortBy?: string; sortDir?: string }): Observable<UmsPageResponse<UmsUserSummary>> {
    return this.getAllUsers(page, size, {
      keyword,
      sortBy: opts?.sortBy,
      sortDir: opts?.sortDir
    });
  }

  getAdminUsers(
    page = 0,
    size = 10,
    sortBy = 'userName',
    sortDir = 'asc'
  ): Observable<UmsPageResponse<UmsUserSummary>> {
    const params = buildUmsListParams({ page, size, sortBy, sortDir });
    return this.http.get<UmsPageResponse<UmsUserSummary>>(`${this.apiUrl}/admins`, { params });
  }

  getUserById(id: number, context?: HttpContext): Observable<UmsUser> {
    return this.http.get<UmsUser>(`${this.apiUrl}/${id}`, { context });
  }

  createUser(user: Partial<UmsUser> & { passwordHash?: string }): Observable<UmsUser> {
    return this.http.post<UmsUser>(`${this.apiUrl}/`, user, handled());
  }

  updateUser(id: number, user: Partial<UmsUser>): Observable<UmsUser> {
    return this.http.put<UmsUser>(`${this.apiUrl}/${id}`, user, handled());
  }

  deleteUser(id: number): Observable<any> {
    return this.http.delete(`${this.apiUrl}/${id}`, handled());
  }

  // ── S-04: access reviews ─────────────────────────────────────────────────

  listAccessReviews(): Observable<AccessReview[]> {
    return this.http.get<AccessReview[]>(`${this.reviewsUrl}`);
  }

  getAccessReview(id: number): Observable<{ review: AccessReview; items: AccessReviewItem[] }> {
    return this.http.get<{ review: AccessReview; items: AccessReviewItem[] }>(`${this.reviewsUrl}/${id}`);
  }

  decideAccessReviewItem(reviewId: number, itemId: number, decision: string, note: string | null): Observable<AccessReviewItem> {
    return this.http.post<AccessReviewItem>(`${this.reviewsUrl}/${reviewId}/items/${itemId}`, { decision, note }, handled());
  }

  closeAccessReview(id: number): Observable<AccessReview> {
    return this.http.post<AccessReview>(`${this.reviewsUrl}/${id}/close`, {}, handled());
  }

  openAccessReviews(): Observable<{ period: string; opened: number }> {
    return this.http.post<{ period: string; opened: number }>(`${this.reviewsUrl}/open`, {}, handled());
  }

  private get reviewsUrl(): string {
    return this.apiUrl.replace(/\/users\/?$/, '') + '/access-reviews';
  }

  /** O-16: clears the account's second factor; it enrols again at its next sign-in. */
  resetMfa(id: number): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.apiUrl}/${id}/mfa/reset`, {}, handled());
  }

  /** O-16: whether the account has a second factor enrolled, and how many recovery codes it has left. */
  getMfaStatus(id: number): Observable<UmsMfaStatus> {
    return this.http.get<UmsMfaStatus>(`${this.apiUrl}/${id}/mfa/status`);
  }

  /**
   * Lifts a lockout: the failure count, the ladder rung and the timed lock go, and
   * an account the ladder had closed returns to ACTIVE. The only way out of the
   * ladder's last rung (V080); the account page offers it on a LOCKED account.
   */
  unlockUser(id: number): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.apiUrl}/${id}/unlock`, {}, handled());
  }

  changePassword(userId: number, newPassword: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/${userId}/change-password`, { newPassword }, handled());
  }

  // ========== Group Assignment ==========

  getUserGroups(userId: number): Observable<UmsGroup[]> {
    return this.http.get<UmsGroup[]>(`${this.apiUrl}/${userId}/groups`);
  }

  assignUserToGroup(userId: number, groupId: number): Observable<any> {
    return this.http.post(`${this.apiUrl}/${userId}/groups/${groupId}`, {}, handled());
  }

  removeUserFromGroup(userId: number, groupId: number): Observable<any> {
    return this.http.delete(`${this.apiUrl}/${userId}/groups/${groupId}`, handled());
  }

  // ========== Role Assignment ==========

  getUserRoles(userId: number): Observable<UmsRole[]> {
    return this.http.get<UmsRole[]>(`${this.apiUrl}/${userId}/roles`);
  }

  /** R-21: an optional window (ISO date-times) on the assignment. */
  assignRoleToUser(userId: number, roleId: number, window?: { validFrom?: string | null; validTo?: string | null; reason?: string | null }): Observable<any> {
    return this.http.post(`${this.apiUrl}/${userId}/roles/${roleId}`, window ?? {}, handled());
  }

  /** R-21: copy this user's roles to a colleague until validTo; privileged roles come back as pending requests. */
  delegateRoles(userId: number, body: { toUserId: number; validFrom?: string | null; validTo: string; reason?: string | null }):
    Observable<{ applied: string[]; pendingRequestIds: number[]; alreadyHeld: string[]; status: string }> {
    return this.http.post<{ applied: string[]; pendingRequestIds: number[]; alreadyHeld: string[]; status: string }>(
      `${this.apiUrl}/${userId}/delegate`, body, handled());
  }

  removeRoleFromUser(userId: number, roleId: number): Observable<any> {
    return this.http.delete(`${this.apiUrl}/${userId}/roles/${roleId}`, handled());
  }

  // ========== Country Assignment ==========

  getUserCountries(userId: number): Observable<UmsUserCountry[]> {
    return this.http.get<UmsUserCountry[]>(`${this.apiUrl}/${userId}/countries`);
  }

  assignCountryToUser(userId: number, countryId: number): Observable<any> {
    return this.http.post(`${this.apiUrl}/${userId}/countries/${countryId}`, {}, handled());
  }

  removeCountryFromUser(userId: number, countryId: number): Observable<any> {
    return this.http.delete(`${this.apiUrl}/${userId}/countries/${countryId}`, handled());
  }

  // ========== Direct Permission Assignment ==========

  getUserDirectPermissions(userId: number): Observable<UmsPermission[]> {
    return this.http.get<UmsPermission[]>(`${this.apiUrl}/${userId}/permissions`);
  }

  grantPermissionToUser(userId: number, permissionId: number): Observable<any> {
    return this.http.post(`${this.apiUrl}/${userId}/permissions/${permissionId}`, {}, handled());
  }

  revokePermissionFromUser(userId: number, permissionId: number): Observable<any> {
    return this.http.delete(`${this.apiUrl}/${userId}/permissions/${permissionId}`, handled());
  }

  /**
   * R-14: a direct grant is a time-boxed exception. `reason` and `expiresAt`
   * (ISO date) are required by the server unless every code is a dashboard
   * section (per-user configuration).
   */
  grantPermissionsToUser(userId: number, permissionIds: number[], reason?: string, expiresAt?: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/${userId}/permissions/batch`, {
      permissionIds,
      reason: reason || null,
      expiresAt: expiresAt || null
    }, handled());
  }

  /** R-13: the direct-grant rows of one account with reason and expiry. */
  getUserDirectGrants(userId: number): Observable<UmsDirectGrant[]> {
    return this.http.get<UmsDirectGrant[]>(`${this.apiUrl}/${userId}/permissions/grants`);
  }

  /** N-03: active staff holding a position (and a country), among those the caller can see. */
  getApproverCandidates(positionCode?: string | null, countryId?: number | null): Observable<UmsUserOption[]> {
    let params = new HttpParams();
    if (positionCode) { params = params.set('positionCode', positionCode); }
    if (countryId != null) { params = params.set('countryId', String(countryId)); }
    return this.http.get<UmsUserOption[]>(`${this.apiUrl}/approver-candidates`, { params });
  }

  // ========== Effective permissions (R-13) ==========

  private get effectiveUrl(): string {
    return `${environment.apiUrl}/mohe/api/v1/ums/effective-permissions`;
  }

  getEffectivePermissions(userId: number): Observable<EffectivePermissionReport> {
    return this.http.get<EffectivePermissionReport>(`${this.effectiveUrl}/users/${userId}`);
  }

  getMyEffectivePermissions(): Observable<EffectivePermissionReport> {
    return this.http.get<EffectivePermissionReport>(`${this.effectiveUrl}/me`);
  }

  diffEffectivePermissions(userId: number, otherId: number): Observable<EffectivePermissionDiff> {
    return this.http.get<EffectivePermissionDiff>(`${this.effectiveUrl}/users/${userId}/diff/${otherId}`);
  }

  getDirectGrantExceptions(): Observable<DirectGrantException[]> {
    return this.http.get<DirectGrantException[]>(`${this.effectiveUrl}/exceptions`);
  }

  getSeparationOfDuties(): Observable<SeparationOfDutiesReport> {
    return this.http.get<SeparationOfDutiesReport>(`${this.effectiveUrl}/separation-of-duties`);
  }

  revokePermissionsFromUser(userId: number, permissionIds: number[]): Observable<any> {
    return this.http.delete(`${this.apiUrl}/${userId}/permissions/batch`, {
      body: { permissionIds },
      ...handled()
    });
  }
}
