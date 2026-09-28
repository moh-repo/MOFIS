import { entityName, entityTypeLabel, EntityTypeCodeLists } from './approval-entity-label';
import { codeRow } from '../../../testing/code-list.stub';
import { EnumOption } from '../../service/code-list.service';

/**
 * Naming on the three approval screens (live M9).
 *
 * <p>Approval History showed `STUDENT_DATA` where Pending Approvals showed
 * "بيانات الطالب": the `/completed` endpoint does not serialise the lazy config,
 * and even with it the English label was the raw code. The entity-name column
 * showed a bare id for every finance and fee row, whose payloads name the
 * student under `studentFinName` / `studyUniversityFeeStudentName`.
 */
describe('approval entity labels', () => {

    describe('entityTypeLabel', () => {
        const rows: Record<string, EnumOption> = {
            STUDENT_DATA: codeRow('STUDENT_DATA', { labelAr: 'بيانات الطالب', labelEn: 'Student data' }),
            STUDENT_FINANCE: codeRow('STUDENT_FINANCE', { labelAr: 'البيانات المالية', labelEn: 'Student finance' })
        };
        /** Only `find` and `label`, shaped like CodeListService's (case-insensitive, code when unknown). */
        const codeLists: EntityTypeCodeLists = {
            find: (list, code) => list === 'approval-entity-type' && code != null && code !== ''
                ? rows[String(code).trim().toUpperCase()]
                : undefined,
            label: (list, code, lang) => {
                const row = codeLists.find(list, code);
                if (!row) {
                    return code == null ? '' : String(code);
                }
                return lang === 'en' ? row.labelEn : row.labelAr;
            }
        };

        it('names the type from the code list in the reader\'s language', () => {
            const request = { entityType: 'STUDENT_DATA', config: { entityType: 'STUDENT_DATA', entityTypeAr: 'من الإعداد' } };

            expect(entityTypeLabel(codeLists, request, true)).toBe('بيانات الطالب');
            expect(entityTypeLabel(codeLists, request, false)).toBe('Student data');
        });

        it('does not need the config: the history endpoint leaves it out', () => {
            expect(entityTypeLabel(codeLists, { entityType: 'STUDENT_FINANCE', config: null }, true)).toBe('البيانات المالية');
            expect(entityTypeLabel(codeLists, { entityType: 'student_finance' }, false)).withContext('case does not matter').toBe('Student finance');
        });

        it('falls back to the config\'s names for a type the list does not know', () => {
            const request = { entityType: 'WAFAR_THING', config: { entityType: 'Wafar thing', entityTypeAr: 'شيء وفر' } };

            expect(entityTypeLabel(codeLists, request, true)).toBe('شيء وفر');
            expect(entityTypeLabel(codeLists, request, false)).toBe('Wafar thing');
            expect(entityTypeLabel(codeLists, { entityType: 'WAFAR_THING', config: { entityType: 'Wafar thing' } }, true))
                .withContext('no Arabic name on the config').toBe('Wafar thing');
        });

        it('shows the raw code when neither the list nor a config names it', () => {
            expect(entityTypeLabel(codeLists, { entityType: 'WAFAR_THING', config: null }, true)).toBe('WAFAR_THING');
            expect(entityTypeLabel(codeLists, { entityType: 'WAFAR_THING' }, false)).toBe('WAFAR_THING');
        });

        it('is blank for no request or no type', () => {
            expect(entityTypeLabel(codeLists, null, true)).toBe('');
            expect(entityTypeLabel(codeLists, undefined, false)).toBe('');
            expect(entityTypeLabel(codeLists, { config: { entityType: 'X' } }, false)).toBe('X');
            expect(entityTypeLabel(codeLists, {}, true)).toBe('');
        });
    });

    describe('entityName', () => {

        it('names a finance row by studentFinName', () => {
            expect(entityName({ id: 41, entityId: '3', requestData: '{"id":3,"studentFinName":"زيد علي"}' })).toBe('زيد علي');
        });

        it('names a university-fee row by studyUniversityFeeStudentName', () => {
            expect(entityName({ id: 42, entityId: '9', requestData: '{"studyUniversityFeeStudentName":"دعاء"}' })).toBe('دعاء');
        });

        it('unwraps the onboarding envelope', () => {
            expect(entityName({ id: 43, requestData: '{"studentData":{"studentName":"X"},"pendingUploadSessionId":"s1"}' })).toBe('X');
        });

        it('keeps the old preference order for the known keys', () => {
            expect(entityName({ requestData: '{"studentName":"أحمد","name":"n","studentCode":"C1"}' })).toBe('أحمد');
            expect(entityName({ requestData: '{"name":"قائمة","documentName":"d"}' })).toBe('قائمة');
            expect(entityName({ requestData: '{"documentName":"جواز"}' })).toBe('جواز');
            expect(entityName({ requestData: '{"studentCode":"S-77"}' })).toBe('S-77');
            expect(entityName({ requestData: '{"studentCode":77}' })).withContext('a numeric code is text').toBe('77');
        });

        it('accepts any other *StudentName string, but not a blank one', () => {
            expect(entityName({ requestData: '{"studentLoanStudentName":"سالم"}' })).toBe('سالم');
            expect(entityName({ entityId: '5', requestData: '{"studentLoanStudentName":"   "}' })).toBe('5');
            expect(entityName({ entityId: '5', requestData: '{"studentnameStudentNameX":"no"}' })).withContext('the suffix is case-sensitive and terminal').toBe('5');
        });

        it('reads the original side when the proposed side names nothing (a DELETE)', () => {
            expect(entityName({ entityId: '8', requestData: '{}', originalData: '{"studentFinName":"هند"}' })).toBe('هند');
            expect(entityName({ entityId: '8', originalData: '{"studentName":"هند"}' })).toBe('هند');
        });

        it('falls back to the entity id, then the request id', () => {
            expect(entityName({ id: 44, entityId: '123' })).toBe('123');
            expect(entityName({ id: 44, entityId: '123', requestData: 'not json' })).toBe('123');
            expect(entityName({ id: 44, entityId: '123', requestData: '{"studentName":""}' })).withContext('a blank name is no name').toBe('123');
            expect(entityName({ id: 44 })).toBe('44');
            expect(entityName({ id: 44, requestData: '[1,2]' })).withContext('an array payload names nothing').toBe('44');
            expect(entityName({})).toBe('');
            expect(entityName(null)).toBe('');
        });
    });
});
