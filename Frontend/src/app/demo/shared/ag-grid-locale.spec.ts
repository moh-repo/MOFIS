import { AllCommunityModule, GridApi, ModuleRegistry, createGrid } from 'ag-grid-community';
import en from '../../../assets/i18n/en.json';
import ar from '../../../assets/i18n/ar.json';
import { AG_GRID_TEXT_KEYS, agGridLocaleText } from './ag-grid-locale';

ModuleRegistry.registerModules([AllCommunityModule]);

function lookup(dictionary: unknown, key: string): string | undefined {
    const value = key.split('.').reduce<unknown>(
        (node, part) => (node && typeof node === 'object' ? (node as Record<string, unknown>)[part] : undefined),
        dictionary
    );
    return typeof value === 'string' ? value : undefined;
}

const arabic = (key: string) => lookup(ar, key) ?? key;
const ARABIC = /[؀-ۿ]/;

describe('agGridLocaleText (live QA R5-14)', () => {

    it('has an English and an Arabic text for every key it maps', () => {
        for (const [gridKey, key] of Object.entries(AG_GRID_TEXT_KEYS)) {
            expect(lookup(en, key)).withContext(`${gridKey} → ${key} (en)`).toBeTruthy();
            expect(lookup(ar, key)).withContext(`${gridKey} → ${key} (ar)`).toMatch(ARABIC);
        }
    });

    it('covers the words QA saw in English on the Arabic Financial Data Form', () => {
        const text = agGridLocaleText(arabic);
        for (const gridKey of ['pageSizeSelectorLabel', 'page', 'of', 'to', 'filterOoo', 'contains', 'equals', 'noRowsToShow']) {
            expect(text[gridKey]).withContext(gridKey).toMatch(ARABIC);
        }
    });

    it('leaves out a key with no translation, so the grid keeps its own English for it', () => {
        const text = agGridLocaleText(key => (key === 'AG_GRID.PAGE' ? 'صفحة' : key));
        expect(text).toEqual({ page: 'صفحة' });
    });

    describe('in a real grid', () => {
        let host: HTMLDivElement;
        let api: GridApi | undefined;

        beforeEach(() => {
            host = document.createElement('div');
            host.style.width = '800px';
            host.style.height = '400px';
            document.body.appendChild(host);
        });

        afterEach(() => {
            api?.destroy();
            host.remove();
        });

        it('draws the pagination panel in Arabic', async () => {
            api = createGrid(host, {
                columnDefs: [{ field: 'name' }],
                rowData: Array.from({ length: 45 }, (_, i) => ({ name: `row ${i}` })),
                pagination: true,
                paginationPageSize: 20,
                paginationPageSizeSelector: [20, 50],
                theme: 'legacy', // as the pages set it
                enableRtl: true,
                localeText: agGridLocaleText(arabic)
            });
            await new Promise(resolve => setTimeout(resolve, 50));

            const panel = host.querySelector('.ag-paging-panel')?.textContent ?? '';
            expect(panel).toContain('حجم الصفحة');
            expect(panel).toContain('صفحة');
            expect(panel).not.toContain('Page Size');
            expect(panel).not.toMatch(/\bPage\b|\bof\b|\bto\b/);
        });
    });
});
