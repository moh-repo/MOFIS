import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';

/**
 * The shared summary band, defined in assets/layout/styles/_summary-cards.scss.
 *
 * <p>Two pages showed a row of figures above their table and both laid it out
 * with `auto-fit`, which cannot place a known number of cards tidily: it picks
 * a column count from the available width and strands whatever does not divide
 * into it. With five cards it emits six tracks, the last of them empty — that
 * empty track is the orphan.
 *
 * <p>The structure is now shared and the palettes stay with the pages, because
 * the colours mean something local (approved/rejected on one, alone/with-family
 * on the other). So these tests cover layout and the accent plumbing, and say
 * nothing about which colour any particular card is.
 */
@Component({
    template: `
    <div class="summary-cards" [class]="gridClass" [attr.dir]="dir" [style.width.px]="width">
      @for (c of cards; track c) {
        <div class="summary-card" [style.--summary-accent]="accent">
          <div class="summary-icon"><i></i></div>
          <div class="summary-content">
            <span class="summary-label">{{ c }}</span>
            <span class="summary-value">1234</span>
          </div>
        </div>
      }
    </div>
  `,
    standalone: false
})
class SummaryCardsHostComponent {
    cards = ['a', 'b', 'c', 'd'];
    gridClass = 'summary-cards summary-cards--cols-4';
    accent = 'rgb(34, 197, 94)';
    dir = 'ltr';
    width = 1000;
}

describe('shared summary cards', () => {

    let fixture: any;
    let host: SummaryCardsHostComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [SummaryCardsHostComponent]
        }).compileComponents();

        fixture = TestBed.createComponent(SummaryCardsHostComponent);
        host = fixture.componentInstance;
        // Attached to the document, or nothing has a computed layout at all.
        document.body.appendChild(fixture.nativeElement);
        fixture.detectChanges();
    });

    afterEach(() => fixture.nativeElement.remove());

    const el = (sel: string) => fixture.nativeElement.querySelector(sel) as HTMLElement;
    const tracks = () =>
        getComputedStyle(el('.summary-cards')).gridTemplateColumns.split(/\s+/);

    const show = (n: number, cols: number) => {
        host.cards = Array.from({ length: n }, (_, i) => `c${i}`);
        host.gridClass = `summary-cards summary-cards--cols-${cols}`;
        fixture.detectChanges();
    };

    it('is a grid at all', () => {
        // Establish the stylesheet is loaded before anything below leans on it.
        expect(getComputedStyle(el('.summary-cards')).display).toBe('grid');
    });

    it('declares as many columns as the modifier asks for', () => {
        // Viewport-independent on purpose: a wrong column count and a right one
        // agree at some widths, so measuring where the cards land proves little.
        expect(tracks().length).withContext(`tracks: ${tracks().join(' | ')}`).toBe(4);

        show(5, 5);
        expect(tracks().length).withContext(`tracks: ${tracks().join(' | ')}`).toBe(5);
    });

    it('leaves no empty track for the cards to rattle around in', () => {
        // auto-fit's signature failure: more tracks than there are cards, the
        // spare ones collapsed to 0px.
        for (const [n, cols] of [[3, 3], [4, 4], [5, 5], [6, 6]] as [number, number][]) {
            show(n, cols);
            expect(tracks().length).withContext(`${n} cards, ${cols} columns`).toBe(n);
            expect(tracks().every(t => parseFloat(t) > 0))
                .withContext(`tracks: ${tracks().join(' | ')}`).toBeTrue();
        }
    });

    /*
     * The two-column step lives between 560px and 1200px, and Karma's window is
     * fixed at 1600. Resizing the container does not help: these are viewport
     * media queries, so the wide step keeps winning however narrow the box is.
     *
     * An iframe carries its own viewport, so the media queries evaluate against
     * its width. The parent's stylesheets are cloned into it — the app's CSS
     * arrives as <style> elements Karma injects, so copying those nodes brings
     * the real rules along rather than a restatement of them.
     */
    function renderAt(viewport: number, cards: number): { tracks: string[]; lastShare: number } {
        const frame = document.createElement('iframe');
        frame.style.cssText = `width:${viewport}px; height:600px; border:0;`;
        document.body.appendChild(frame);

        const doc = frame.contentDocument!;
        doc.open();
        doc.write('<!doctype html><html><head></head><body style="margin:0"></body></html>');
        doc.close();

        // The rule text is copied out of the CSSOM rather than by cloning the
        // <style> nodes, so what reaches the iframe is the rules the browser
        // actually parsed. adoptedStyleSheets is included because global styles
        // can arrive that way and are not part of document.styleSheets at all.
        const sheets: CSSStyleSheet[] = [
            ...(Array.from(document.styleSheets) as CSSStyleSheet[]),
            ...(((document as any).adoptedStyleSheets ?? []) as CSSStyleSheet[])
        ];
        const css = sheets.map(sheet => {
            try {
                return Array.from(sheet.cssRules).map(r => r.cssText).join('\n');
            } catch {
                // A sheet the browser will not expose; nothing to copy.
                return '';
            }
        }).join('\n');

        if (!css.includes('.summary-cards')) {
            // Fail loudly rather than silently measuring an unstyled iframe,
            // which would report one column and look like a layout bug.
            throw new Error('app stylesheet did not reach the iframe: '
                + `${sheets.length} sheets, ${css.length} chars collected`);
        }

        const style = doc.createElement('style');
        style.textContent = css;
        doc.head.appendChild(style);

        const card = '<div class="summary-card"><div class="summary-icon"></div>'
            + '<div class="summary-content"><span class="summary-value">1</span></div></div>';
        doc.body.innerHTML =
            `<div class="summary-cards summary-cards--cols-${cards}">${card.repeat(cards)}</div>`;

        const grid = doc.querySelector('.summary-cards') as HTMLElement;
        const all = Array.from(doc.querySelectorAll('.summary-card')) as HTMLElement[];
        const last = all[all.length - 1];

        const result = {
            tracks: frame.contentWindow!.getComputedStyle(grid).gridTemplateColumns.split(/\s+/),
            lastShare: last.getBoundingClientRect().width / grid.getBoundingClientRect().width
        };

        frame.remove();
        return result;
    }

    it('spans the last card across a two-column row when the count is odd', () => {
        // Five cards over two columns strands the fifth. The rule keys off
        // :last-child:nth-child(odd), so it works out the parity itself.
        const { tracks: t, lastShare } = renderAt(700, 5);

        expect(t.length).withContext(`tracks at 700px: ${t.join(' | ')}`).toBe(2);
        expect(lastShare).withContext('the odd card should take the whole row')
            .toBeGreaterThan(0.9);
    });

    it('does not span the last card when the count is even', () => {
        const { tracks: t, lastShare } = renderAt(700, 4);

        expect(t.length).toBe(2);
        expect(lastShare).withContext('an even set divides without help')
            .toBeLessThan(0.6);
    });

    it('stacks to a single column on a phone', () => {
        const { tracks: t } = renderAt(420, 4);

        expect(t.length).withContext(`tracks at 420px: ${t.join(' | ')}`).toBe(1);
    });

    it('paints the edge bar from the accent the page sets', () => {
        // The accent is declared once per card and read here, by the icon tile
        // and by the hover border — so a page sets one value, not three.
        const bar = getComputedStyle(el('.summary-card'), '::before');

        expect(bar.content).withContext('the edge bar should exist').not.toBe('none');
        expect(bar.backgroundColor).toBe(host.accent);
    });

    it('puts the edge bar on the leading edge in both directions', () => {
        // A logical inset, so `dir` alone moves it. A physical `left` would put
        // the bar on the wrong side of every Arabic page.
        const barLeft = () => {
            const card = el('.summary-card').getBoundingClientRect();
            const inset = getComputedStyle(el('.summary-card'), '::before');
            return { card, start: inset.insetInlineStart };
        };

        expect(barLeft().start).toBe('0px');

        host.dir = 'rtl';
        fixture.detectChanges();

        // Same declaration, opposite physical edge — which is the point.
        expect(getComputedStyle(el('.summary-card'), '::before').right).toBe('0px');
    });

    it('keeps the digits the same width across cards', () => {
        // The values sit in a row and get read against each other.
        expect(getComputedStyle(el('.summary-value')).fontVariantNumeric)
            .toContain('tabular-nums');
    });
});
