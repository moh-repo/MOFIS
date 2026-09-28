import { filenameFromDisposition } from '../student-portal-api.service';

/**
 * Live QA L12 (P15, 25 Sep 2026): a reply attachment was saved as "response-document",
 * with no extension. The portal now saves it under the name the server gives in
 * Content-Disposition, which the server encodes as UTF-8 so an Arabic name survives.
 */
describe('Portal downloads — the file\'s own name (L12, P15)', () => {

    it('reads the UTF-8 name first', () => {
        const header = "attachment; filename=\"=?UTF-8?Q?x?=\"; filename*=UTF-8''%D8%B1%D8%AF%D9%91%D9%8A.pdf";
        expect(filenameFromDisposition(header)).toBe('ردّي.pdf');
    });

    it('falls back to the plain name, and to nothing', () => {
        expect(filenameFromDisposition('attachment; filename="answer.png"')).toBe('answer.png');
        expect(filenameFromDisposition(null)).toBe('');
        expect(filenameFromDisposition('attachment')).toBe('');
    });
});
