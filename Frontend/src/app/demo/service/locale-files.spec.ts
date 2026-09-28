/**
 * The shipped locale files parse, and say what they mean.
 *
 * <p>`TranslationService.initialize` fetches `assets/i18n/en.json` and
 * `ar.json` at startup and, when one of them cannot be read, logs the error
 * and carries on with the handful of hard-coded chart fallbacks for that
 * language. Nothing else notices: the build passes, every spec that mocks
 * HttpClient passes, and the application comes up with `LAYOUT.PROFILE`,
 * `NAVIGATION.HOME` and every other key printed raw in that language. That
 * is what happened on 25 Sep 2026 when four English strings were given a
 * `\’` — a backslash before a typographic apostrophe, which JSON does not
 * allow — and the whole English interface fell back to key names.
 *
 * <p>`npm run validate-i18n` parses both files and CI runs it, but the unit
 * suite did not, so a broken file survived every local check. The files are
 * read here the way the application reads them: served by karma from the
 * test build's assets, fetched over HTTP, parsed by the browser.
 *
 * <p>The second check is the fault this one replaced. Before the bad escape,
 * the same four strings were written `\\u2019`: a valid file whose values
 * contain the six characters `’`, shown to the reader as typed. No
 * translation has a reason to contain a backslash, so any that does is one
 * of these.
 */
describe('the locale files', () => {

    const load = async (name: string): Promise<{ text: string; parsed: unknown }> => {
        const response = await fetch(`assets/i18n/${name}`, { cache: 'no-store' });
        expect(response.ok).withContext(`${name}: HTTP ${response.status}`).toBeTrue();
        const text = await response.text();
        let parsed: unknown;
        try {
            parsed = JSON.parse(text);
        } catch (e) {
            fail(`${name} is not valid JSON: ${(e as Error).message}`);
        }
        return { text, parsed };
    };

    /** Every string value, with the path that leads to it. */
    const values = (node: unknown, path: string[] = []): { path: string; value: string }[] => {
        if (typeof node === 'string') {
            return [{ path: path.join('.'), value: node }];
        }
        if (node && typeof node === 'object') {
            return Object.entries(node as Record<string, unknown>)
                .flatMap(([key, child]) => values(child, [...path, key]));
        }
        return [];
    };

    for (const name of ['en.json', 'ar.json']) {

        it(`${name} is valid JSON with a body of translations`, async () => {
            const { parsed } = await load(name);
            expect(parsed).withContext('parsed value').toEqual(jasmine.any(Object));
            const all = values(parsed);
            expect(all.length).withContext('number of strings').toBeGreaterThan(1000);
            // The shell's own keys are the ones a broken file makes most visible.
            expect(all.some(v => v.path === 'LAYOUT.PROFILE')).withContext('LAYOUT.PROFILE present').toBeTrue();
        });

        it(`${name} has no translation that contains a backslash`, async () => {
            const { parsed } = await load(name);
            const withBackslash = values(parsed).filter(v => v.value.includes('\\'));
            expect(withBackslash)
                .withContext('these would show the escape to the reader, not the character')
                .toEqual([]);
        });
    }
});
