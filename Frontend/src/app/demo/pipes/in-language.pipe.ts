import { Pipe, PipeTransform } from '@angular/core';
import { TranslationService } from '../service/translation.service';

/**
 * The English or the Arabic of a value the server sends in both, as `field` and
 * `fieldEn`: `{{ progress | inLanguage:'currentYearLabel' }}`.
 *
 * <p>Pages picked the Arabic field and printed it in both languages — Current
 * Year, wafar type, quarter names (QA B1, B2, B3). The English one falls back to
 * the Arabic when it is empty, which is what an untranslated row should show.
 *
 * <p>Impure, like `translate`: the portal switches language in place, and a pure
 * pipe would keep the first language's text until the row itself changed.
 */
@Pipe({ name: 'inLanguage', standalone: true, pure: false })
export class InLanguagePipe implements PipeTransform {

    constructor(private translationService: TranslationService) {}

    transform(row: Record<string, any> | null | undefined, field: string): string {
        return pickLanguage(row, field, this.translationService.getCurrentLanguage());
    }
}

/**
 * Copies of option rows with `field` holding the current language's text, for a
 * dropdown whose `optionLabel` names that field. The rows' other fields, the id
 * among them, are untouched.
 */
export function localizeField<T extends Record<string, any>>(rows: T[] | null | undefined, field: string, language: string): T[] {
    return (rows ?? []).map(row => ({ ...row, [field]: pickLanguage(row, field, language) }));
}

/** The same choice, for code that is not a template (exports, chart labels). */
export function pickLanguage(row: Record<string, any> | null | undefined, field: string, language: string): string {
    if (!row) {
        return '';
    }
    const arabic = row[field];
    const english = row[field + 'En'];
    const blank = (v: unknown) => v === null || v === undefined || String(v).trim() === '';
    if (language === 'en' && !blank(english)) {
        return String(english);
    }
    return blank(arabic) ? (blank(english) ? '' : String(english)) : String(arabic);
}
