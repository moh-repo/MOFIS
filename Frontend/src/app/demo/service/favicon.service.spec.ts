import { of, throwError } from 'rxjs';
import { FaviconService } from './favicon.service';
import { SysSettingsDto } from './system-settings.service';

/**
 * The tab icon, and the three ways it can go wrong.
 *
 * Declaring a second `rel="icon"` does not replace the first — the browser picks
 * between every icon on offer — so an uploaded logo has to remove the static
 * ones. And a link pointing at a URL that does not resolve leaves the tab blank
 * rather than falling back, so nothing is swapped until the image has loaded.
 *
 * A real <img> load is what these assert against; the served assets stand in for
 * the logo endpoint, which karma has no backend for.
 */
describe('FaviconService', () => {

    /** Served by karma from src/assets, so it genuinely loads. */
    const LOADABLE = 'assets/icons/favicon-32.png';
    const BROKEN = 'assets/icons/there-is-no-such-icon.png';

    let added: HTMLLinkElement[];

    const settings = (dto: Partial<SysSettingsDto> | null, url = LOADABLE) => ({
        getSettings: () => (dto === null ? throwError(() => new Error('offline')) : of(dto as SysSettingsDto)),
        getLogoUrl: () => url
    });

    /** The links index.html ships, rebuilt so each test starts from the same page. */
    const givenStaticIcons = () => {
        ['favicon.ico', 'assets/icons/favicon-32.png'].forEach(href => {
            const link = document.createElement('link');
            link.rel = 'icon';
            link.href = href;
            document.head.appendChild(link);
            added.push(link);
        });
        const touch = document.createElement('link');
        touch.rel = 'apple-touch-icon';
        touch.href = 'assets/icons/apple-touch-icon.png';
        document.head.appendChild(touch);
        added.push(touch);
    };

    const icons = () => Array.from(document.head.querySelectorAll('link[rel~="icon"]')) as HTMLLinkElement[];

    beforeEach(() => {
        added = [];
        // Karma's own page has no icon links, so the only ones present are ours.
        icons().forEach(link => link.remove());
        givenStaticIcons();
    });

    afterEach(() => {
        added.forEach(link => link.remove());
        document.head.querySelectorAll('link[rel~="icon"]').forEach(link => link.remove());
    });

    it('leaves the static icons alone when no logo has been uploaded', () => {
        new FaviconService(settings({ sysLogoPath: null, sysLogoFilename: null }) as any).sync();

        expect(icons().length).toBe(2);
    });

    it('leaves the static icons alone when settings cannot be read', () => {
        new FaviconService(settings(null) as any).sync();

        expect(icons().length).toBe(2);
    });

    it('replaces every static icon with the uploaded logo', (done) => {
        new FaviconService(settings({ sysLogoPath: '/logos/x.png', sysLogoFilename: 'x.png' }) as any).sync();

        setTimeout(() => {
            const links = icons();
            expect(links.length).toBe(1);
            expect(links[0].href).toContain(LOADABLE);
            done();
        }, 400);
    });

    it('busts the cache with the uploaded filename', (done) => {
        new FaviconService(settings({ sysLogoPath: '/logos/x.png', sysLogoFilename: 'brand-v2.png' }) as any).sync();

        setTimeout(() => {
            expect(icons()[0].href).toContain('v=brand-v2.png');
            done();
        }, 400);
    });

    it('keeps the static icons when the logo will not load', (done) => {
        new FaviconService(
            settings({ sysLogoPath: '/logos/x.png', sysLogoFilename: 'x.png' }, BROKEN) as any
        ).sync();

        setTimeout(() => {
            const links = icons();
            expect(links.length).toBe(2);
            expect(links.some(l => l.href.includes('there-is-no-such-icon'))).toBe(false);
            done();
        }, 400);
    });

    it('does not disturb the home-screen icon', (done) => {
        new FaviconService(settings({ sysLogoPath: '/logos/x.png', sysLogoFilename: 'x.png' }) as any).sync();

        setTimeout(() => {
            expect(document.head.querySelectorAll('link[rel="apple-touch-icon"]').length).toBe(1);
            done();
        }, 400);
    });
});
