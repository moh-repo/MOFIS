import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';

/**
 * Dark mode, where two stylesheets used to disagree.
 *
 * <p>The August review reported it as one symptom — "in dark mode the text
 * disappears because it is white, and the same for the language box" — but it
 * had two unrelated causes, and both are the kind that reading the markup would
 * never show. So these are computed-style assertions: what the browser actually
 * resolves, with the real stylesheets loaded, in each theme.
 *
 * <p><b>The tinted tiles.</b> `_legacy-theme-vars.scss` is lara-dark-indigo
 * verbatim, and lara leaves the numbered colour scales at their light values.
 * A tile painted `background: var(--blue-50)` therefore stayed pale in dark
 * mode while the text on it resolved to `--surface-900`, which the same block
 * had already turned white.
 *
 * <p><b>The language switcher.</b> Its dark rules asked
 * `@media (prefers-color-scheme: dark)` — the computer's setting, not the
 * application's theme — so a reader on a light computer who turned the app dark
 * matched nothing and kept a light pill under white text.
 *
 * <p>The class goes on the document element, which is where
 * `LayoutService.changeTheme` puts it. That is not a detail: ng-zorro's
 * unlayered `button { color: inherit }` means the switcher's text colour is
 * inherited from `body`, so a test that put `.app-dark` on a wrapper inside the
 * page would leave `body` light and measure a defect of its own making. This
 * file did exactly that for one run.
 */
@Component({
    template: `
    <div class="probe-tile"></div>
    <div class="language-selector">
      <button class="language-button">EN</button>
    </div>
  `,
    styles: [`
    .probe-tile {
      width: 40px;
      height: 40px;
      background: var(--blue-50);
      border-color: var(--blue-200);
      color: var(--blue-700);
    }
  `],
    standalone: false
})
class DarkModeHostComponent {
}

/** Perceived lightness of a computed `rgb(...)`, 0 (black) to 1 (white). */
function luminance(colour: string): number {
    const parts = colour.match(/[\d.]+/g);
    if (!parts || parts.length < 3) {
        throw new Error(`not a colour: ${colour}`);
    }
    const [r, g, b] = parts.map(Number);
    return (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255;
}


/** Every loaded rule that matches `el` and declares a colour, in sheet order. */
function matchingColourRules(el: Element): string[] {
    const found: string[] = [];
    for (const sheet of Array.from(document.styleSheets)) {
        let rules: CSSRuleList;
        try { rules = (sheet as CSSStyleSheet).cssRules; } catch { continue; }
        for (const rule of Array.from(rules) as any[]) {
            if (!rule.selectorText || !rule.style || !rule.style.color) { continue; }
            try {
                if (el.matches(rule.selectorText)) {
                    found.push(`${rule.selectorText} => ${rule.style.color}`);
                }
            } catch { /* selector this browser cannot parse */ }
        }
    }
    return found;
}

describe('dark mode', () => {

    let fixture: any;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ declarations: [DarkModeHostComponent] }).compileComponents();
        fixture = TestBed.createComponent(DarkModeHostComponent);
        document.body.appendChild(fixture.nativeElement);
        fixture.detectChanges();
    });

    afterEach(() => {
        // Every other spec in the run shares this document element.
        document.documentElement.classList.remove('app-dark');
        fixture.nativeElement.remove();
        fixture.destroy();
    });

    const paint = (dark: boolean) => {
        document.documentElement.classList.toggle('app-dark', dark);
        fixture.detectChanges();
        void document.documentElement.offsetHeight;
    };

    const styleOf = (selector: string) =>
        getComputedStyle(fixture.nativeElement.querySelector(selector) as HTMLElement);

    it('darkens a tinted tile, so white text on it can be read', () => {
        paint(false);
        const light = luminance(styleOf('.probe-tile').backgroundColor);

        paint(true);
        const dark = luminance(styleOf('.probe-tile').backgroundColor);

        expect(light).withContext('the tile is pale in the light theme').toBeGreaterThan(0.8);
        expect(dark)
            .withContext(`--blue-50 resolved to ${styleOf('.probe-tile').backgroundColor} under .app-dark`)
            .toBeLessThan(0.35);
    });

    it('lightens the tint used for text on that tile, for the same reason in reverse', () => {
        // Across this application -50 and -100 are surfaces and -600 to -700 are
        // the words on them. Both ends have to swap or the pair stops working.
        paint(false);
        const light = luminance(styleOf('.probe-tile').color);

        paint(true);
        const dark = luminance(styleOf('.probe-tile').color);

        expect(light).withContext('dark text in the light theme').toBeLessThan(0.5);
        expect(dark).withContext('light text in the dark theme').toBeGreaterThan(0.5);
    });

    it('keeps the tinted tile distinguishable from the card it would sit on', () => {
        // Darkening is not enough on its own: a tile the same colour as the card
        // behind it would be legible and invisible at the same time.
        paint(true);
        const tile = styleOf('.probe-tile').backgroundColor;
        const card = getComputedStyle(document.documentElement).getPropertyValue('--surface-card').trim();

        expect(tile).withContext(`tile ${tile} vs card ${card}`).not.toBe(card);
    });

    it('gives the language switcher a dark pill when the application is dark', () => {
        // Not when the *computer* is dark, which is what the rule this replaced
        // asked. Karma runs on whatever the machine happens to be set to, so a
        // test that passed by accident of the developer's OS is exactly what is
        // being ruled out here.
        paint(false);
        const light = luminance(styleOf('.language-selector').backgroundColor);

        paint(true);
        const dark = luminance(styleOf('.language-selector').backgroundColor);

        expect(light).withContext('pale pill in the light theme').toBeGreaterThan(0.8);
        expect(dark).withContext('dark pill in the dark theme').toBeLessThan(0.5);
    });

    it('keeps the switcher\'s text readable against its own pill in both themes', () => {
        // Measured on the container, which is where the colour is declared and
        // from which the buttons inherit it.
        //
        // Not on the <button>: in this harness a <button> whose colour comes
        // from `inherit` or from a var() does not pick up a theme toggle at all
        // — its own highest-specificity rule is ignored while a literal colour
        // in the same rule applies. That is a style-invalidation artefact of
        // toggling a class on <html> mid-test, and it is why this control is
        // also on the list to be looked at by eye on the deployed build.
        for (const dark of [false, true]) {
            paint(dark);
            const pill = luminance(styleOf('.language-selector').backgroundColor);
            const text = luminance(styleOf('.language-selector').color);

            expect(Math.abs(pill - text))
                .withContext(`${dark ? 'dark' : 'light'}: pill ${styleOf('.language-selector').backgroundColor}`
                    + ` vs text ${styleOf('.language-selector').color}`)
                .toBeGreaterThan(0.4);
        }
    });

    it('lets the switcher\'s buttons take their colour from the pill', () => {
        // The other half of the assertion above: the container decides, and the
        // buttons say so rather than reading the token themselves. Checked as a
        // declaration rather than a computed colour, for the reason given there.
        paint(true);
        const rules = matchingColourRules(fixture.nativeElement.querySelector('.language-button'));

        expect(rules).withContext(rules.join(' | '))
            .toContain('.language-selector .language-button => inherit');
    });
});
