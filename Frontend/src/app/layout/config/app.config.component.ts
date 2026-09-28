import { Component, Input } from '@angular/core';
import { LayoutService } from '../service/app.layout.service';
import { MenuService } from '../app.menu.service';
import { MenuVariantService } from '../menu/menu-variant.service';
import { MenuVariant } from '../menu/menu-model';
import { TranslationService } from '../../demo/service/translation.service';

@Component({
    selector: 'app-config',
    templateUrl: './app.config.component.html',
    standalone: false
})
export class AppConfigComponent {
    @Input() minimal: boolean = false;

    // The old 12–16 steps scaled by 0.9, so the default (12.6) still sits in the
    // middle and the +/- buttons still disable at the ends.
    scales: number[] = [10.8, 11.7, 12.6, 13.5, 14.4];

    constructor(
        public layoutService: LayoutService,
        public menuService: MenuService,
        private translationService: TranslationService,
        private menuVariantService: MenuVariantService
    ) {}

    /* Which sidebar this person sees. Kept out of AppConfig on purpose: the rest
       of that object is per-session and resets on reload, and this choice is
       meant to stick. */
    get menuVariant(): MenuVariant {
        return this.menuVariantService.variant;
    }
    set menuVariant(val: MenuVariant) {
        this.menuVariantService.variant = val;
    }

    /* The drawer mirrors with the rest of the layout. layout-rtl.css used to do
       this by pinning the panel to left: 0 while p-drawer still animated it as a
       right-hand panel, so in Arabic it slid in from the wrong side. */
    get drawerPosition(): 'left' | 'right' {
        return this.translationService.isRTL() ? 'left' : 'right';
    }

    get visible(): boolean {
        return this.layoutService.state.configSidebarVisible;
    }
    set visible(_val: boolean) {
        this.layoutService.state.configSidebarVisible = _val;
    }

    get scale(): number {
        return this.layoutService.config().scale;
    }
    set scale(_val: number) {
        this.layoutService.config.update((config) => ({
            ...config,
            scale: _val,
        }));
    }

    get menuMode(): string {
        return this.layoutService.config().menuMode;
    }
    set menuMode(_val: string) {
        this.layoutService.config.update((config) => ({
            ...config,
            menuMode: _val,
        }));
    }

    get inputStyle(): string {
        return this.layoutService.config().inputStyle;
    }
    set inputStyle(_val: string) {
        this.layoutService.config().inputStyle = _val;
    }

    get ripple(): boolean {
        return this.layoutService.config().ripple;
    }
    set ripple(_val: boolean) {
        this.layoutService.config.update((config) => ({
            ...config,
            ripple: _val,
        }));
    }

    set colorScheme(val: string) {
        this.layoutService.config.update((config) => ({
            ...config,
            colorScheme: val,
        }));
    }
    get colorScheme(): string {
        return this.layoutService.config().colorScheme;
    }

    get darkMode(): boolean {
        return this.colorScheme === 'dark';
    }
    set darkMode(val: boolean) {
        this.colorScheme = val ? 'dark' : 'light';
    }

    // Step through `scales` by index rather than by a fixed amount — the steps are
    // no longer whole numbers, so ++/-- would walk off the list and leave the
    // buttons permanently enabled.
    decrementScale() {
        this.stepScale(-1);
    }

    incrementScale() {
        this.stepScale(1);
    }

    private stepScale(direction: number) {
        const i = this.scales.indexOf(this.scale);
        const next = (i === -1 ? this.scales.indexOf(12.6) : i) + direction;
        if (next >= 0 && next < this.scales.length) {
            this.scale = this.scales[next];
        }
    }
}
