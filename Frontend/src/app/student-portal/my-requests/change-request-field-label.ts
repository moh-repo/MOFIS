import { CodeListService, EnumOption } from '../../demo/service/code-list.service';

const LIST = 'change-request-field';

/** What the resolver needs of the code-list service; a real CodeListService satisfies it. */
export type ChangeRequestFieldCodeLists = Pick<CodeListService, 'find' | 'rows' | 'label'>;

/**
 * The name of a change-request field in the reader's language (live M6).
 *
 * <p>A request stores the field's code and the label it was submitted under,
 * in the language of the submitter. A current code is named by the
 * `change-request-field` list. Older requests were stored under an earlier
 * vocabulary (`cellphone` where the list now says `studentCellphone`) with the
 * English label beside it, so that label — and, failing that, the code itself
 * — is matched back against every row's names to find the row it meant. Only
 * a field no row names in either language shows as it was stored.
 */
export function resolveChangeRequestFieldLabel(
    codeLists: ChangeRequestFieldCodeLists,
    fieldName: string | null | undefined,
    stored?: string | null
): string {
    if (fieldName && codeLists.find(LIST, fieldName)) {
        return codeLists.label(LIST, fieldName);
    }
    const rows = codeLists.rows(LIST);
    const row = findByName(rows, stored) ?? findByName(rows, fieldName);
    if (row) {
        return codeLists.label(LIST, row.value);
    }
    return stored || fieldName || '';
}

function findByName(rows: EnumOption[], text: string | null | undefined): EnumOption | undefined {
    const needle = normalise(text);
    if (!needle) {
        return undefined;
    }
    return rows.find(row =>
        normalise(row.labelEn) === needle
        || normalise(row.labelAr) === needle
        || normalise(row.label) === needle
    );
}

function normalise(text: string | null | undefined): string {
    return (text ?? '').trim().toLowerCase();
}
