/**
 * The shell at tablet width (live QA #11).
 *
 * <p>At 768px the sidebar is an off-canvas sheet, so the page should use the full
 * width. In English a 270px empty gutter stayed beside it: the desktop rule for a
 * visible static sidebar is one class more specific than the tablet rule, so it
 * won at every width. In Arabic the sidebar kept its 2rem desktop inset while it
 * slid away by its own width, and a 2rem strip of it covered the first column.
 *
 * <p>The suite's browser is 1600px wide, so the shell is laid out inside a 768px
 * iframe carrying the application's own stylesheets: media queries answer to the
 * frame's width, which is the whole point.
 */
describe('the shell at 768px', () => {

    let frame: HTMLIFrameElement;

    const load = async (): Promise<Document> => {
        frame = document.createElement('iframe');
        frame.style.width = '768px';
        frame.style.height = '600px';
        frame.style.position = 'absolute';
        frame.style.left = '-10000px';
        document.body.appendChild(frame);
        const doc = frame.contentDocument!;
        const loads: Promise<unknown>[] = [];
        document.querySelectorAll('style, link[rel="stylesheet"]').forEach(node => {
            const copy = node.cloneNode(true) as HTMLElement;
            if (copy instanceof HTMLLinkElement) {
                copy.href = (node as HTMLLinkElement).href;
                loads.push(new Promise(resolve => { copy.onload = copy.onerror = resolve; }));
            }
            doc.head.appendChild(copy);
        });
        // Measured at rest, not halfway through the sidebar's slide.
        const still = doc.createElement('style');
        still.textContent = '* { transition: none !important; }';
        doc.head.appendChild(still);
        await Promise.all(loads);
        return doc;
    };

    const shell = (doc: Document, rtl: boolean, extra = '') => {
        doc.documentElement.classList.toggle('rtl', rtl);
        doc.body.innerHTML = `
          <div class="layout-wrapper layout-static ${rtl ? 'rtl' : ''} ${extra}" dir="${rtl ? 'rtl' : 'ltr'}">
            <div class="layout-sidebar">menu</div>
            <div class="layout-main-container"><div class="layout-main">content</div></div>
          </div>`;
        const win = frame.contentWindow!;
        const container = doc.querySelector('.layout-main-container') as HTMLElement;
        const sidebar = doc.querySelector('.layout-sidebar') as HTMLElement;
        return {
            margins: () => {
                const s = win.getComputedStyle(container);
                return { left: parseFloat(s.marginLeft), right: parseFloat(s.marginRight) };
            },
            sidebar: () => sidebar.getBoundingClientRect(),
            // The viewport's own width: an Arabic document puts its scrollbar on
            // the left, so clientWidth would stop short of the right edge.
            width: () => win.innerWidth
        };
    };

    afterEach(() => frame?.remove());

    it('is actually 768px wide in the frame', async () => {
        const doc = await load();
        expect(frame.contentWindow!.matchMedia('(max-width: 991px)').matches).toBeTrue();
        expect(doc.documentElement.clientWidth).toBeLessThanOrEqual(768);
    });

    it('English: the page takes the width, no gutter for a sidebar that is not there', async () => {
        const s = shell(await load(), false);

        expect(s.margins().left).withContext('the desktop sidebar margin survived at 768px').toBe(0);
        expect(s.sidebar().right).withContext('the closed sidebar is on screen').toBeLessThanOrEqual(0);
    });

    it('Arabic: no strip of the closed sidebar over the first column', async () => {
        const s = shell(await load(), true);

        expect(s.margins().right).toBe(0);
        expect(s.sidebar().left)
            .withContext(`the closed sidebar starts at ${s.sidebar().left}px, inside the ${s.width()}px page`)
            .toBeGreaterThanOrEqual(s.width());
    });

    it('Arabic: opened, the sidebar sits flush with the right edge', async () => {
        const s = shell(await load(), true, 'layout-mobile-active');

        expect(Math.round(s.sidebar().right)).toBe(s.width());
    });
});
