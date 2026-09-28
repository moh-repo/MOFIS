import { CodeListService } from '../../service/code-list.service';

/**
 * How the three approval screens (pending, history, my requests) name a
 * request's entity type and the record it is about. One copy, so the list
 * page, the history page and the details dialog agree (live M9).
 */

const ENTITY_TYPE_LIST = 'approval-entity-type';

/** The part of an ApprovalRequest the type label reads. */
export interface EntityTypeLabelSource {
    entityType?: string;
    config?: { entityType?: string; entityTypeAr?: string } | null;
}

/** The part of an ApprovalRequest the entity name reads. */
export interface EntityNameSource {
    requestData?: string | null;
    originalData?: string | null;
    entityId?: string | null;
    id?: number;
}

/** What the label needs of the code-list service; a real CodeListService satisfies it. */
export type EntityTypeCodeLists = Pick<CodeListService, 'find' | 'label'>;

/**
 * The entity type in the reader's language.
 *
 * <p>The `approval-entity-type` code list names every type in both languages,
 * so it decides first. The config's own names are the fallback for a type the
 * list does not know — and the `/completed` endpoint does not serialise the
 * lazy config at all, which is why the raw code is the last resort rather than
 * the usual answer.
 */
export function entityTypeLabel(
    codeLists: EntityTypeCodeLists,
    request: EntityTypeLabelSource | null | undefined,
    arabic: boolean
): string {
    const code = request?.entityType ?? '';
    if (code && codeLists.find(ENTITY_TYPE_LIST, code)) {
        return codeLists.label(ENTITY_TYPE_LIST, code, arabic ? 'ar' : 'en');
    }
    const config = request?.config;
    if (config) {
        return (arabic && config.entityTypeAr) ? config.entityTypeAr : (config.entityType || code);
    }
    return code;
}

/**
 * Payload keys that name the record, in order of preference. The first four
 * are the student/document/lookup shapes; finance rows carry `studentFinName`
 * and university-fee rows `studyUniversityFeeStudentName`.
 */
const NAME_KEYS = [
    'studentName',
    'name',
    'documentName',
    'studentCode',
    'studentFinName',
    'studyUniversityFeeStudentName'
];

/**
 * The name of the record a request is about, read from its JSON payload.
 *
 * <p>Reads the proposed side first and the original side second (a DELETE may
 * have no proposed side). Onboarding requests wrap the record in an envelope
 * (`{ studentData, pendingUploadSessionId }`), so that is unwrapped before
 * looking. After the known keys, any own `…StudentName` string is accepted,
 * so a payload shape added later still names its student. Only then does the
 * bare entity id show, and the request id last of all.
 */
export function entityName(request: EntityNameSource | null | undefined): string {
    if (!request) {
        return '';
    }
    return pickName(request.requestData)
        ?? pickName(request.originalData)
        ?? request.entityId
        ?? (request.id != null ? String(request.id) : '');
}

function pickName(raw: string | null | undefined): string | null {
    if (!raw) {
        return null;
    }
    let data: unknown;
    try {
        data = JSON.parse(raw);
    } catch {
        return null;
    }
    if (isRecord(data) && isRecord(data['studentData'])) {
        data = data['studentData'];
    }
    if (!isRecord(data)) {
        return null;
    }
    for (const key of NAME_KEYS) {
        const text = asText(data[key]);
        if (text) {
            return text;
        }
    }
    for (const key of Object.keys(data)) {
        if (key.endsWith('StudentName') && !NAME_KEYS.includes(key)) {
            const text = asText(data[key]);
            if (text) {
                return text;
            }
        }
    }
    return null;
}

function isRecord(value: unknown): value is Record<string, unknown> {
    return value != null && typeof value === 'object' && !Array.isArray(value);
}

/** A non-blank string or a number, as text; anything else is not a name. */
function asText(value: unknown): string | null {
    if (typeof value === 'string') {
        return value.trim() ? value : null;
    }
    if (typeof value === 'number') {
        return String(value);
    }
    return null;
}
