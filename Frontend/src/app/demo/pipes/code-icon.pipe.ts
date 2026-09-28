import { Pipe, PipeTransform } from '@angular/core';
import { CodeListService } from '../service/code-list.service';

/**
 * `<i class="pi {{ code | codeIcon:'<list>' }}">` — the PrimeIcons name a code
 * list row carries (`ICON`), or '' when it has none.
 */
@Pipe({ name: 'codeIcon', standalone: true, pure: true })
export class CodeIconPipe implements PipeTransform {

    constructor(private codeLists: CodeListService) { }

    transform(code: string | number | null | undefined, list: string): string {
        return this.codeLists.icon(list, code) ?? '';
    }
}
