import { Component } from '@angular/core';
import { LayoutService } from "./service/app.layout.service";
import { SYSTEM_LOGO_FALLBACK } from '../demo/service/system-logo';

@Component({
    selector: 'app-footer',
    templateUrl: './app.footer.component.html',
    standalone: false
})
export class AppFooterComponent {
    /** See {@link SYSTEM_LOGO_FALLBACK} — one emblem, both colour schemes. */
    readonly logoUrl = SYSTEM_LOGO_FALLBACK;

    /**
     * The copyright year, read at load rather than written into the text
     * (QA C1 / BRD-003). The agreed wording names 2026; a footer that still
     * says so in 2027 is the kind of thing nobody is assigned to notice.
     */
    readonly year = new Date().getFullYear();

    constructor(public layoutService: LayoutService) { }
}
