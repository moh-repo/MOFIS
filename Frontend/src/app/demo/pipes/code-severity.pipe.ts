import { Pipe, PipeTransform } from '@angular/core';
import { CodeListService, PrimeSeverity } from '../service/code-list.service';

/**
 * `[severity]="code | codeSeverity:'<list>'"` — the PrimeNG tag severity a
 * code list row carries (`UI_SEVERITY`). Anything outside PrimeNG's vocabulary,
 * or an unknown code, renders `secondary`.
 */
@Pipe({ name: 'codeSeverity', standalone: true, pure: true })
export class CodeSeverityPipe implements PipeTransform {

    constructor(private codeLists: CodeListService) { }

    transform(code: string | number | null | undefined, list: string): PrimeSeverity {
        return this.codeLists.severity(list, code);
    }
}
