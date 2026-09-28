import { CodeListService, EnumOption } from '../demo/service/code-list.service';

/** A code-list row for a spec: names derived from the code, everything else overridable. */
export function codeRow(value: string, patch: Partial<EnumOption> = {}): EnumOption {
    return {
        label: `${value}-ar`, value, labelAr: `${value}-ar`, labelEn: `${value}-en`,
        sortOrder: 0, uiSeverity: null, icon: null, isSystem: false, active: true, extra: null, ...patch
    };
}

/**
 * A CodeListService warmed with `payload` and no HTTP behind it, for specs
 * whose templates use the codeLabel / codeSeverity / codeIcon pipes. Provide it
 * as `{ provide: CodeListService, useValue: codeListStub({...}) }`.
 */
export function codeListStub(payload: Record<string, EnumOption[]> = {}, lang = 'en'): CodeListService {
    const service = new CodeListService(
        null as never,
        null as never,
        { getCurrentLanguage: () => lang } as never
    );
    service.store(payload);
    return service;
}
