import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { MenuVariant } from './menu-model';
import { UmsAuthService } from '../../demo/service/ums-auth.service';

/** New users get the reorganised sidebar; classic stays one click away. */
const DEFAULT_VARIANT: MenuVariant = 'proposed';

const STORAGE_PREFIX = 'mohe.menuVariant';

/**
 * Which sidebar the signed-in person is looking at.
 *
 * The choice is per person, per browser: it is not a system setting and never
 * reaches the backend. Storage is namespaced by user id so two people sharing a
 * workstation don't inherit each other's menu.
 *
 * Every storage access is guarded — a private window, cleared site data, or a
 * browser configured to block site data all throw on access rather than
 * returning null, and none of that is a reason to fail to render a sidebar.
 */
@Injectable({ providedIn: 'root' })
export class MenuVariantService {

    private readonly variantSource = new BehaviorSubject<MenuVariant>(DEFAULT_VARIANT);

    /** Emits on every change, including the first read from storage. */
    readonly variant$ = this.variantSource.asObservable();

    constructor(private authService: UmsAuthService) {
        this.variantSource.next(this.read());

        // A different user in the same browser has a different stored choice.
        this.authService.currentUser$.subscribe(() => {
            const stored = this.read();
            if (stored !== this.variantSource.value) {
                this.variantSource.next(stored);
            }
        });
    }

    get variant(): MenuVariant {
        return this.variantSource.value;
    }

    set variant(value: MenuVariant) {
        const next: MenuVariant = value === 'classic' ? 'classic' : 'proposed';
        if (next === this.variantSource.value) {
            return;
        }
        this.write(next);
        this.variantSource.next(next);
    }

    /** True while the reorganised sidebar is showing — gates collapsible roots and search. */
    get isProposed(): boolean {
        return this.variantSource.value === 'proposed';
    }

    private storageKey(): string {
        const id = this.authService.currentUserValue?.id;
        return id ? `${STORAGE_PREFIX}.${id}` : STORAGE_PREFIX;
    }

    private read(): MenuVariant {
        try {
            const raw = localStorage.getItem(this.storageKey());
            return raw === 'classic' || raw === 'proposed' ? raw : DEFAULT_VARIANT;
        } catch {
            // Storage unavailable — the switch still works for this session.
            return DEFAULT_VARIANT;
        }
    }

    private write(value: MenuVariant): void {
        try {
            localStorage.setItem(this.storageKey(), value);
        } catch {
            // Nothing to do: the in-memory subject is still the source of truth.
        }
    }
}
