import { Pipe, PipeTransform } from '@angular/core';
import { CodeListService } from '../service/code-list.service';

/**
 * `{{ code | codeLabel:'<list>':lang }}` — the name of a code from the loaded
 * code lists. Pure: pass `lang` (bind `currentLanguage` from
 * `translationService.currentLanguage$`) so the view re-evaluates on a
 * language switch. Falls back to the other language, then to the code.
 */
@Pipe({ name: 'codeLabel', standalone: true, pure: true })
export class CodeLabelPipe implements PipeTransform {

    constructor(private codeLists: CodeListService) { }

    transform(code: string | number | null | undefined, list: string, lang?: string): string {
        return this.codeLists.label(list, code, lang);
    }
}
