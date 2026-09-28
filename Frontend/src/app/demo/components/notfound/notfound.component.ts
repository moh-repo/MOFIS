import { Component } from '@angular/core';
import { SYSTEM_LOGO_FALLBACK } from '../../service/system-logo';

@Component({
    selector: 'app-notfound',
    templateUrl: './notfound.component.html',
    standalone: false
})
export class NotfoundComponent {
    /** The system's own emblem, not the template's (QA C2 / UI-003). */
    readonly logoUrl = SYSTEM_LOGO_FALLBACK;
}
