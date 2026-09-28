import { Injectable } from '@angular/core';
import { CanActivate, Router, UrlTree } from '@angular/router';
import { Observable, of, race, timer } from 'rxjs';
import { map, switchMap, take } from 'rxjs/operators';
import { StudentPortalApiService } from './student-portal-api.service';
import { CodeListService } from '../demo/service/code-list.service';

/** Upper bound on waiting for the code lists before the portal page opens. */
const CODE_LISTS_WAIT_MS = 5000;

@Injectable({ providedIn: 'root' })
export class StudentPortalGuard implements CanActivate {

  constructor(
    private api: StudentPortalApiService,
    private router: Router,
    private codeLists: CodeListService
  ) {}

  canActivate(): Observable<boolean | UrlTree> | boolean | UrlTree {
    if (!this.api.isLoggedIn()) {
      return this.router.createUrlTree(['/student-portal/login']);
    }
    // A token in storage is not a session: it may have expired hours ago. The
    // first navigation of a page load settles that with the server, and later
    // ones are answered from the result.
    return this.api.restoreSession().pipe(
      take(1),
      switchMap(valid => valid
        ? this.codeListsReady()
        : of(this.router.createUrlTree(['/student-portal/login'])))
    );
  }

  /** The portal names statuses and inquiry types from the shared code lists. */
  private codeListsReady(): Observable<boolean | UrlTree> {
    if (this.codeLists.isLoaded) {
      return of(true);
    }
    // The student's own token authenticates the call — never the staff JWT.
    const token = this.api.getToken();
    const headers = token ? { 'X-Student-Portal-Token': token } : undefined;
    return race(
      this.codeLists.ensureLoaded(headers).pipe(take(1)),
      timer(CODE_LISTS_WAIT_MS)
    ).pipe(take(1), map(() => true));
  }
}
