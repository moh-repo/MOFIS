/**
 * The language a report opens in: the one the reader is working in (decision
 * DEC-10, QA Q-8 / B6). Every report page started in Arabic, so an English reader
 * got an Arabic document unless they noticed the switch above the Generate button.
 */
export function uiReportLang(translation: { getCurrentLanguage(): string }): 'ar' | 'en' {
    return translation.getCurrentLanguage() === 'en' ? 'en' : 'ar';
}
