import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';

/**
 * Which side the shell reserves for the sidebar.
 *
 * <p>In Arabic the sidebar is on the right, and the main container has to keep
 * its margin on the right to leave room for it. It did not: the page slid under
 * the sidebar, and no amount of switching back and forth fixed it — only
 * English looked right.
 *
 * <p>The cause is in `layout-ltr.css`, which guards every one of its rules
 * three ways:
 *
 * <pre>
 *   html:not([dir="rtl"]) .layout-wrapper.layout-static .layout-main-container,
 *   html:not(.rtl)        .layout-wrapper.layout-static .layout-main-container,
 *   .layout-wrapper:not(.rtl).layout-static .layout-main-container { margin-left: … }
 * </pre>
 *
 * The second and third track the language. The first does not and never could:
 * `index.html` ships `<html lang="en" dir="ltr">` and
 * `TranslationService.updateDocumentDirection` deliberately never touches it —
 * setting `dir` on `<html>` moves the browser's own scrollbar to the left. So
 * `html:not([dir="rtl"])` is true in every language, the English rule never
 * switches off, and because it out-specifies `.rtl .layout-main-container`
 * (0,4,1 against 0,2,0) the container ends up reserving space on both sides at
 * once.
 *
 * <p>Measured rather than read off the selectors, because that is the thing
 * that was wrong: each rule looked correct on its own.
 */
@Component({
    template: `
    <div class="layout-wrapper layout-static" [class.rtl]="rtl" [dir]="rtl ? 'rtl' : 'ltr'">
      <div class="layout-sidebar"></div>
      <div class="layout-main-container">
        <div class="layout-main">content</div>
      </div>
    </div>`,
    standalone: false
})
class ShellHost { rtl = false; }

describe('the shell reserves the sidebar side', () => {

    let fixture: any;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ declarations: [ShellHost] }).compileComponents();
        fixture = TestBed.createComponent(ShellHost);
        document.body.appendChild(fixture.nativeElement);
    });

    afterEach(() => {
        fixture.nativeElement.remove();
        // The application puts this on <html>; nothing else in the suite should
        // inherit it.
        document.documentElement.classList.remove('rtl');
    });

    const build = (rtl: boolean) => {
        fixture.componentInstance.rtl = rtl;
        // Both marks, the way TranslationService sets them: the class on <html>
        // and the direction on the wrapper.
        document.documentElement.classList.toggle('rtl', rtl);
        fixture.detectChanges();
    };

    const container = () => fixture.nativeElement.querySelector('.layout-main-container') as HTMLElement;
    const margins = () => {
        const s = getComputedStyle(container());
        return { start: parseFloat(s.marginLeft), end: parseFloat(s.marginRight) };
    };

    it('reserves it on the left in English', () => {
        build(false);
        const { start, end } = margins();
        expect(start).withContext('no room left for the sidebar').toBeGreaterThan(0);
        expect(end).withContext(`reserved ${end}px on the right as well`).toBe(0);
    });

    it('reserves it on the right in Arabic, and only on the right', () => {
        build(true);
        const { start, end } = margins();
        expect(end).withContext('no room left for the sidebar').toBeGreaterThan(0);
        expect(start)
            .withContext(`reserved ${start}px on the left too, so the page runs under the sidebar`)
            .toBe(0);
    });
});
