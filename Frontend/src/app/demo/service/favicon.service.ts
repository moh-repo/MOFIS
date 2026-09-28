import { Injectable } from '@angular/core';
import { SystemSettingsService } from './system-settings.service';

/**
 * Keeps the browser tab icon in step with the system logo.
 *
 * index.html ships the national emblem as static files, which is what the tab
 * shows while the app boots and on any deployment that has not uploaded a logo
 * of its own. Once settings arrive, an uploaded logo takes over — the same one
 * the topbar shows, so the tab and the page agree.
 *
 * The logo endpoint is public (GET /api/v1/sys-settings/logo), so this works on
 * the login screen too, before anyone has signed in.
 */
@Injectable({ providedIn: 'root' })
export class FaviconService {

    constructor(private systemSettingsService: SystemSettingsService) { }

    /**
     * Ask the backend what the logo is and point the tab at it. Safe to call
     * again — after an upload, for instance, so the tab updates without a reload.
     */
    sync(): void {
        this.systemSettingsService.getSettings().subscribe({
            next: dto => {
                if (!dto?.sysLogoPath) {
                    // Nothing uploaded: the static icons in index.html stand.
                    return;
                }
                // The filename changes with each upload, which is what gets the
                // browser to fetch the new image instead of the cached one.
                const url = this.systemSettingsService.getLogoUrl()
                    + '?v=' + encodeURIComponent(dto.sysLogoFilename || '1');
                this.applyWhenLoadable(url);
            },
            // A settings call that fails is not a reason to lose the tab icon.
            error: () => { }
        });
    }

    /**
     * Swap the icon only once the image has actually loaded. Replacing the link
     * with a URL that 404s leaves the tab blank rather than falling back, so the
     * static icons keep the tab until there is something better to show.
     */
    private applyWhenLoadable(url: string): void {
        const probe = new Image();
        probe.onload = () => this.apply(url);
        probe.src = url;
    }

    private apply(url: string): void {
        // Every declared icon is a candidate the browser chooses between, so the
        // static ones have to go rather than sit alongside the uploaded logo.
        // `rel~="icon"` matches `icon` and `shortcut icon` and leaves
        // `apple-touch-icon` — a home-screen tile, not a tab — where it is.
        document.head.querySelectorAll('link[rel~="icon"]').forEach(link => link.remove());

        const link = document.createElement('link');
        link.rel = 'icon';
        // No `type`: the upload may be a PNG, JPEG or SVG, and an attribute that
        // disagrees with the bytes is worse than none at all.
        link.href = url;
        document.head.appendChild(link);
    }
}
