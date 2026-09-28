import { MonoTypeOperatorFunction } from 'rxjs';
import { skip } from 'rxjs/operators';

/**
 * Drops the value a state stream replays to every new subscriber, leaving only the
 * changes that follow.
 *
 * The selected country (`CountrySelectionService`) and the current language
 * (`TranslationService`) are `BehaviorSubject`s: subscribing hands you the value
 * they already hold. A page that loads its data in `ngOnInit` **and** reloads it
 * whenever one of those streams fires therefore ran the same request two or three
 * times on every open — which is what exhausted the report rate limit before a user
 * had pressed anything (review item P3). Reaching the server three times for the
 * same answer is also simply slower.
 *
 * Use it on the subscription, not on the service: the stream must keep replaying for
 * the pages that have no separate initial load and deliberately take their first
 * value from it (the dashboard, comparative statistics, trend analysis).
 *
 * ```ts
 * this.loadMetadata();
 * this.load();
 * this.reportCountryScope.selectedCountryId$
 *   .pipe(changesOnly(), takeUntil(this.destroy$))
 *   .subscribe(() => this.load());
 * ```
 */
export function changesOnly<T>(): MonoTypeOperatorFunction<T> {
  return skip(1);
}
