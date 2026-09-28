/**
 * The `p-input-icon-left` / `p-input-icon-right` compatibility rules.
 *
 * <p>Up to PrimeNG 17 these were plain CSS classes the library styled globally.
 * v18 replaced them with `<p-iconfield>` and stopped styling the classes, and
 * 41 usages across 27 templates in this app kept using them — the search box on
 * nearly every list page. With nothing behind them the wrapper was an ordinary
 * inline span, so the icon sat *beside* the field rather than inside it and the
 * input reserved no room for it. The build stayed green throughout; no compiler
 * can see a dead CSS class.
 *
 * <p>The rules live in styles.scss and are asserted here rather than in any one
 * component, because they are global and every one of those 27 pages depends on
 * them. Karma loads the same stylesheet list as the build, including
 * assets/styles/rtl.css, so the mirrored case is real too.
 */
describe('PrimeNG input-icon compatibility', () => {

    let host: HTMLElement;

    /** The markup as the templates actually write it. */
    function build(wrapperClass: string, rtl = false): HTMLElement {
        host = document.createElement('div');
        if (rtl) {
            host.className = 'rtl';
            host.setAttribute('dir', 'rtl');
        }
        host.style.width = '400px';
        host.innerHTML = `
            <span class="${wrapperClass}">
                <i class="pi pi-search"></i>
                <input class="p-inputtext" type="text" />
            </span>`;
        document.body.appendChild(host);
        return host;
    }

    const icon = () => host.querySelector('i') as HTMLElement;
    const input = () => host.querySelector('input') as HTMLInputElement;

    /** True when the icon's centre falls inside the input's box on both axes. */
    function iconSitsInsideInput(): boolean {
        const i = icon().getBoundingClientRect();
        const f = input().getBoundingClientRect();
        const x = i.left + i.width / 2;
        const y = i.top + i.height / 2;
        return x >= f.left && x <= f.right && y >= f.top && y <= f.bottom;
    }

    afterEach(() => host?.remove());

    it('gives the wrapper a positioning context', () => {
        build('p-input-icon-left');

        // Without this the absolutely-positioned icon escapes to the page.
        expect(getComputedStyle(host.firstElementChild as HTMLElement).position).toBe('relative');
    });

    it('puts a left icon inside the field', () => {
        build('p-input-icon-left');

        expect(iconSitsInsideInput())
            .withContext(`icon ${JSON.stringify(icon().getBoundingClientRect())} vs input ${JSON.stringify(input().getBoundingClientRect())}`)
            .toBeTrue();
    });

    it('puts a right icon inside the field', () => {
        build('p-input-icon-right');

        expect(iconSitsInsideInput()).toBeTrue();
    });

    it('reserves padding on the side the icon is on', () => {
        build('p-input-icon-left');
        expect(parseFloat(getComputedStyle(input()).paddingLeft)).toBeGreaterThan(30);

        host.remove();
        build('p-input-icon-right');
        expect(parseFloat(getComputedStyle(input()).paddingRight)).toBeGreaterThan(30);
    });

    it('keeps the icon clear of the text rather than overlapping it', () => {
        build('p-input-icon-left');

        const padding = parseFloat(getComputedStyle(input()).paddingLeft);
        const overlap = icon().getBoundingClientRect().right - input().getBoundingClientRect().left;

        expect(padding).toBeGreaterThan(overlap);
    });

    it('lets clicks through to the field underneath', () => {
        build('p-input-icon-left');

        // The icons in these wrappers are decorative; swallowing the click on
        // the padding would make the left edge of every search box dead.
        expect(getComputedStyle(icon()).pointerEvents).toBe('none');
    });

    it('mirrors to the other side in RTL', () => {
        build('p-input-icon-left', true);

        // assets/styles/rtl.css does the mirroring, and is loaded after
        // styles.scss so its physical overrides win. The icon must still land
        // inside the field, just at the other end of it.
        expect(iconSitsInsideInput()).toBeTrue();

        const fieldCentre = input().getBoundingClientRect().left
            + input().getBoundingClientRect().width / 2;
        const iconCentre = icon().getBoundingClientRect().left + icon().getBoundingClientRect().width / 2;

        expect(iconCentre).withContext('a mirrored icon belongs on the right').toBeGreaterThan(fieldCentre);
        expect(parseFloat(getComputedStyle(input()).paddingRight)).toBeGreaterThan(30);
    });

    it('mirrors from rtl.css alone, so a template must not swap the class itself', () => {
        // rtl.css flips whichever class it is given. Handing it
        // `p-input-icon-right` because the page is Arabic therefore flips a
        // second time and puts the icon back on the left, where it started —
        // which is why the templates that chose between the two on isRTL()
        // ended up unmirrored. `p-input-icon-left` is the one to use in both
        // directions.
        build('p-input-icon-right', true);

        const field = input().getBoundingClientRect();
        const i = icon().getBoundingClientRect();
        const iconCentre = i.left + i.width / 2;

        expect(iconCentre)
            .withContext('p-input-icon-right under .rtl lands on the left — the wrong side')
            .toBeLessThan(field.left + field.width / 2);
    });

    it('spans the row when the template asks it to', () => {
        // `w-full` is on most of these wrappers; an inline span ignores it.
        build('p-input-icon-left w-full');

        const wrapper = host.firstElementChild as HTMLElement;
        expect(getComputedStyle(wrapper).display).toBe('block');
        expect(wrapper.getBoundingClientRect().width).toBeGreaterThan(300);
    });
});
