import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { distinctUntilChanged } from 'rxjs/operators';

const STORAGE_KEY = 'selectedCountryId';

/**
 * Holds the user's selected country context for multi-country scope.
 * Used by report/student APIs and by the country selector in layout and login.
 */
@Injectable({ providedIn: 'root' })
export class CountrySelectionService {
  private readonly subject = new BehaviorSubject<number | null>(this.readStored());

  // distinctUntilChanged prevents clearAuthState() null→null re-emissions from
  // triggering infinite 401 loops in components subscribed to this observable.
  readonly selectedCountryId$: Observable<number | null> = this.subject.asObservable().pipe(distinctUntilChanged());

  get selectedCountryId(): number | null {
    return this.subject.value;
  }

  setSelectedCountryId(id: number | null): void {
    this.subject.next(id);
    if (id != null) {
      localStorage.setItem(STORAGE_KEY, String(id));
    } else {
      localStorage.removeItem(STORAGE_KEY);
    }
  }

  /**
   * Call after login: clear selection only when the stored id is no longer allowed.
   * Default remains null (all countries).
   */
  syncWithAllowedCountryIds(allowedIds: number[]): void {
    const current = this.subject.value;
    if (allowedIds.length === 0) {
      this.setSelectedCountryId(null);
      return;
    }
    if (current != null && !allowedIds.includes(current)) {
      this.setSelectedCountryId(null);
    }
  }

  private readStored(): number | null {
    try {
      const v = localStorage.getItem(STORAGE_KEY);
      if (v == null || v === '') return null;
      const n = parseInt(v, 10);
      return isNaN(n) ? null : n;
    } catch {
      return null;
    }
  }
}
