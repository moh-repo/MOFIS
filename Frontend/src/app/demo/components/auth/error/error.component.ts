import { Component } from '@angular/core';
import { TranslationService } from '../../../service/translation.service';
import { SYSTEM_LOGO_FALLBACK } from '../../../service/system-logo';

@Component({
    selector: 'app-error',
    templateUrl: './error.component.html',
    standalone: false
})
export class ErrorComponent {
    /** The system's own emblem, not the template's (QA C2 / UI-003). */
    readonly logoUrl = SYSTEM_LOGO_FALLBACK;

    constructor(public translationService: TranslationService) {}
}
