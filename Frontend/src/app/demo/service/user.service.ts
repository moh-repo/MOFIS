import { Injectable } from '@angular/core';
import { DataService } from './data.service';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { User, userFromUmsUser } from '../api/user';
import { UmsUser } from '../api/ums';
import { environment } from 'src/environments/environment';

/**
 * User management lives in UMS. This used to point at /api/v1/users/, whose
 * table (MOHECORE.Users) was renamed to Users___old during the UMS migration —
 * every call through here had been failing since.
 */
const APIUrlUser = `${environment.apiUrl}/mohe/api/v1/ums/users/`;

@Injectable({
  providedIn: 'root'
})
export class UserService extends DataService {

  constructor(http: HttpClient) {
    super(APIUrlUser, http);
  }

  /**
   * The signed-in user's own account record, whole.
   *
   * Reads GET /ums/users/me, which any authenticated user may call, rather than
   * /ums/users/{id}: that endpoint sits under UMS_VIEW, so a user without the
   * UMS administration page got a 403 on their own profile page.
   *
   * Returns the UMS record rather than the shell's four-field `User`. The
   * mapping used to happen here, and the profile page — the only caller — was
   * handed a view with no email, no phone, no status and no dates, then filled
   * those rows with `new Date()`. Whoever needs the shell shape can still ask
   * for it through {@link getUserProfile}.
   */
  getMyAccount(): Observable<UmsUser> {
    return this.httpClient.get<UmsUser>(`${this.apiUrl}me`);
  }

  /** The same record reduced to what the shell stores: guards, topbar, localStorage. */
  getUserProfile(): Observable<User> {
    return this.getMyAccount().pipe(map(userFromUmsUser));
  }
}
