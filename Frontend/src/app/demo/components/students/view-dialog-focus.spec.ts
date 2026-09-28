import { getFocusableElements } from '@primeuix/utils/dom';

/**
 * Why the student view dialog opens at its hero instead of its documents table.
 *
 * <p>p-dialog focuses the first focusable element inside its content when it
 * opens — {@code focusOnShow} defaults to true, and the content is searched
 * before the header and the footer. Everything in that dialog down to the
 * documents card is read-only text, so the first focusable thing was a button
 * inside the documents table, several hundred pixels down. Focusing an element
 * scrolls it into view, and p-dialog-content is the scroll container, so the
 * dialog opened at the documents section and the hero was never seen.
 *
 * <p>The fix is a {@code tabindex="0"} on the content container, which puts a
 * tab stop first in document order. These tests pin the two facts about
 * PrimeNG that the fix depends on, so that an upgrade which changes the
 * focusable selector fails here rather than silently in the browser.
 *
 * <p>They do not assert that the attribute is still on the real template —
 * a Karma spec cannot read it, and a fixture repeating the markup would only
 * agree with itself. The reasoning lives in a comment on the element.
 */
describe('view dialog focus target', () => {

    let content: HTMLElement;

    /** The dialog content as it is really shaped: prose, then a button. */
    function build(containerAttrs: string): HTMLElement {
        const host = document.createElement('div');
        host.innerHTML = `
            <div class="p-dialog-content">
                <div class="sv" ${containerAttrs}>
                    <div class="sv__hero"><h2>Student name</h2><img alt="" /></div>
                    <div class="sv__card"><span>Read-only detail</span></div>
                    <div class="sv__docs"><button type="button">Download</button></div>
                </div>
            </div>`;
        document.body.appendChild(host);
        return host.querySelector('.p-dialog-content') as HTMLElement;
    }

    afterEach(() => {
        content?.parentElement?.remove();
    });

    it('picks the documents button when the container has no tab stop', () => {
        content = build('');

        const first = getFocusableElements(content)[0] as HTMLElement;

        // This is the bug: the first focusable thing is deep in the page, and
        // focusing it scrolls the dialog past the hero.
        expect(first.tagName).toBe('BUTTON');
    });

    it('picks the container itself once it carries tabindex="0"', () => {
        content = build('tabindex="0"');

        const first = getFocusableElements(content)[0] as HTMLElement;

        expect(first.classList).toContain('sv');
    });

    it('does not accept tabindex="-1" as a focus target', () => {
        // The usual "focus the dialog container" idiom is tabindex="-1", and it
        // does not work here: PrimeNG excludes it from the selector explicitly.
        content = build('tabindex="-1"');

        const first = getFocusableElements(content)[0] as HTMLElement;

        expect(first.tagName).toBe('BUTTON');
    });
});
