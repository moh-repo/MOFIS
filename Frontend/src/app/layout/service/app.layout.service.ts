import { Injectable, effect, signal } from '@angular/core';
import { Subject } from 'rxjs';

export interface AppConfig {
    inputStyle: string;
    /** 'light' | 'dark' — the only two schemes since the PrimeNG 18 theming rewrite. */
    colorScheme: string;
    ripple: boolean;
    menuMode: string;
    scale: number;
}

/** Class PrimeNG's Aura preset watches for dark mode; see providePrimeNG in app.module.ts. */
const DARK_MODE_CLASS = 'app-dark';

interface LayoutState {
    staticMenuDesktopInactive: boolean;
    overlayMenuActive: boolean;
    profileSidebarVisible: boolean;
    configSidebarVisible: boolean;
    staticMenuMobileActive: boolean;
    menuHoverActive: boolean;
}

@Injectable({
    providedIn: 'root',
})
export class LayoutService {
    _config: AppConfig = {
        ripple: false,
        inputStyle: 'outlined',
        menuMode: 'static',
        colorScheme: 'light',
        // Must match $scale in assets/layout/styles/layout/_variables.scss: this is
        // applied as an inline font-size on <html> and so overrides the stylesheet.
        scale: 12.6,
    };

    config = signal<AppConfig>(this._config);

    state: LayoutState = {
        staticMenuDesktopInactive: false,
        overlayMenuActive: false,
        profileSidebarVisible: false,
        configSidebarVisible: false,
        staticMenuMobileActive: false,
        menuHoverActive: false,
    };

    private configUpdate = new Subject<AppConfig>();

    private overlayOpen = new Subject<any>();

    configUpdate$ = this.configUpdate.asObservable();

    overlayOpen$ = this.overlayOpen.asObservable();

    constructor() {
        effect(() => {
            const config = this.config();
            // Idempotent class toggle, so apply unconditionally — this also settles the
            // initial scheme, which the updateStyle() guard skipped.
            this.changeTheme();
            this.changeScale(config.scale);
            this.onConfigUpdate();
        });
    }

    updateStyle(config: AppConfig) {
        return config.colorScheme !== this._config.colorScheme;
    }

    onMenuToggle() {
        if (this.isOverlay()) {
            this.state.overlayMenuActive = !this.state.overlayMenuActive;
            if (this.state.overlayMenuActive) {
                this.overlayOpen.next(null);
            }
        }

        if (this.isDesktop()) {
            this.state.staticMenuDesktopInactive =
                !this.state.staticMenuDesktopInactive;
        } else {
            this.state.staticMenuMobileActive =
                !this.state.staticMenuMobileActive;

            if (this.state.staticMenuMobileActive) {
                this.overlayOpen.next(null);
            }
        }
    }

    showProfileSidebar() {
        this.state.profileSidebarVisible = !this.state.profileSidebarVisible;
        if (this.state.profileSidebarVisible) {
            this.overlayOpen.next(null);
        }
    }

    showConfigSidebar() {
        this.state.configSidebarVisible = true;
    }

    isOverlay() {
        return this.config().menuMode === 'overlay';
    }

    isDesktop() {
        return window.innerWidth > 991;
    }

    isMobile() {
        return !this.isDesktop();
    }

    onConfigUpdate() {
        this._config = { ...this.config() };
        this.configUpdate.next(this.config());
    }

    /**
     * Applies the current colour scheme.
     *
     * Up to PrimeNG 17 this swapped a prebuilt theme stylesheet at runtime. Since the
     * v18 theming rewrite the palette is generated from the Aura preset at build time
     * and dark mode is a single class on <html>, so there is no stylesheet to swap.
     */
    changeTheme() {
        document.documentElement.classList.toggle(
            DARK_MODE_CLASS,
            this.config().colorScheme === 'dark'
        );
    }

    changeScale(value: number) {
        document.documentElement.style.fontSize = `${value}px`;
    }
}
