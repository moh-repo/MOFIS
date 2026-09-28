import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, throwError } from 'rxjs';
import { User, userFromUmsUser } from '../api/user';
import { Router } from '@angular/router';
import { UmsAuthService } from './ums-auth.service';
import { UmsUser } from '../api/ums';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private currentUserSubject: BehaviorSubject<User | null>;
  public currentUser: Observable<User | null>;

  constructor(
    private router: Router,
    private umsAuthService: UmsAuthService
  ) {
    this.currentUserSubject = new BehaviorSubject<User | null>(null);
    this.currentUser = this.currentUserSubject.asObservable();

    // A projection of the UMS session, not a second copy of it. This used to be
    // read from `localStorage.currentUser` — the same account the UMS service had
    // already stored under `umsCurrentUser`, written twice and able to disagree
    // with itself. Both are memory-only now; `UmsAuthService.restoreSession` fills
    // them from `/me` before the first navigation.
    this.umsAuthService.profile$.subscribe(response => {
      if (response.user) {
        this.currentUserSubject.next(userFromUmsUser(response.user));
      }
    });
    // Signing out, a 401, or a rejected session clears the UMS side; the shell's
    // view of "who is signed in" has to follow it rather than outlive it.
    this.umsAuthService.currentUser$.subscribe(user => {
      if (user === null) {
        this.currentUserSubject.next(null);
      }
    });
  }

  public get currentUserValue(): User | null {
    return this.currentUserSubject.value;
  }

  /**
   * @deprecated Legacy login removed — use UmsAuthService.login() instead.
   * Credentials must never be sent in URL paths.
   */
  login(_username: string, _password: string): Observable<never> {
    return throwError(() => new Error('Legacy login is disabled. Use UmsAuthService.login().'));
  }

  /**
   * Set legacy currentUser from UMS login response so guards and layout work.
   * Call this after successful UMS login.
   */
  setUserFromUmsResponse(umsUser: UmsUser): void {
    this.currentUserSubject.next(userFromUmsUser(umsUser));
  }

  logout() {

    const userId = this.currentUserValue?.id ?? this.umsAuthService.currentUserValue?.id;

    // Call UMS logout endpoint to terminate session (users/logout may not exist)
    if (userId) {
      this.umsAuthService.notifyLogout(userId);
    }

    this.clearSession();

    this.router.navigate(['/auth/login']);
  }

  /**
   * Forget the signed-in user without telling the backend or navigating. The guard
   * uses this for a profile whose token is gone or expired, where a logout call
   * would fail and the redirect has to carry a returnUrl.
   */
  clearSession(): void {
    // Clear both auth systems so privileges don't leak between users. The UMS call
    // also empties the shell user through the subscription in the constructor;
    // `next(null)` below is kept so a caller does not depend on that ordering.
    this.umsAuthService.clearAuthState();

    try {
      localStorage.removeItem('loginAttempts');
    } catch { /* storage unavailable: there is nothing stored to remove */ }

    this.currentUserSubject.next(null);
  }

  isLoggedIn(): boolean {
    return !!this.currentUserValue;
  }

  hasRole(role: string): boolean {
    return this.currentUserValue?.role?.roleName === role;
  }
} 