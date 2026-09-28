import {
    captureChartCanvas,
    collectChartCards,
    collectDomCards,
    renderChartCard,
} from './chart-capture.util';
import { DataSet, Network } from 'vis-network/standalone';
import { captureElement } from './dom-capture.util';

/** Build a card that mirrors the markup the chart report pages render. */
function buildChartCard(): HTMLElement {
    const card = document.createElement('div');
    card.className = 'surface-card';
    card.style.width = '600px';
    card.innerHTML = `
        <div>
            <h5>Student Enrollment by Country</h5>
            <p>Distribution across host countries</p>
        </div>
        <div class="border-top-1">
            <div class="text-center"><span>Total</span><div>1,240</div></div>
            <div class="text-center"><span>Active</span><div>980</div></div>
        </div>`;

    const canvas = document.createElement('canvas');
    canvas.width = 600;
    canvas.height = 300;
    canvas.style.width = '600px';
    canvas.style.height = '300px';
    const ctx = canvas.getContext('2d')!;
    ctx.fillStyle = '#3b82f6';
    ctx.fillRect(20, 20, 200, 260);
    ctx.fillStyle = '#ef4444';
    ctx.fillRect(240, 80, 200, 200);
    card.insertBefore(canvas, card.querySelector('.border-top-1'));

    return card;
}

function distinctColors(canvas: HTMLCanvasElement): number {
    const { data } = canvas.getContext('2d')!.getImageData(0, 0, canvas.width, canvas.height);
    const seen = new Set<string>();
    for (let i = 0; i < data.length; i += 4) {
        seen.add(`${data[i]},${data[i + 1]},${data[i + 2]}`);
    }
    return seen.size;
}

describe('chart export', () => {
    let host: HTMLElement;

    beforeEach(() => {
        host = document.createElement('div');
        host.id = 'test-print-area';
        document.body.appendChild(host);
    });

    afterEach(() => {
        host.remove();
    });

    it('reads the card heading, description and summary figures around a chart canvas', () => {
        host.appendChild(buildChartCard());

        const captures = collectChartCards(host);

        expect(captures.length).toBe(1);
        expect(captures[0].title).toBe('Student Enrollment by Country');
        expect(captures[0].subtitle).toBe('Distribution across host countries');
        expect(captures[0].stats).toEqual([
            { label: 'Total', value: '1,240' },
            { label: 'Active', value: '980' },
        ]);
    });

    it('skips canvases that are not rendered', () => {
        const card = buildChartCard();
        card.style.display = 'none';
        host.appendChild(card);

        expect(collectChartCards(host).length).toBe(0);
    });

    it('composes a card image larger than the chart and keeps its pixels', () => {
        host.appendChild(buildChartCard());
        const capture = collectChartCards(host)[0];

        const rendered = renderChartCard(capture, { pageTitle: 'Bar Charts', scale: 2 });

        // Banner, heading, description and stat row all add height above the 300px chart.
        expect(rendered.width).toBe(656 * 2);
        expect(rendered.height).toBeGreaterThan(300 * 2);
        expect(distinctColors(rendered)).toBeGreaterThan(2);
    });

    it('renders right-aligned without throwing for RTL locales', () => {
        host.appendChild(buildChartCard());
        const capture = collectChartCards(host)[0];

        const rendered = renderChartCard(capture, { pageTitle: 'الرسوم البيانية', rtl: true, locale: 'ar' });

        expect(rendered.width).toBeGreaterThan(0);
        expect(distinctColors(rendered)).toBeGreaterThan(2);
    });

    it('selects DOM cards and ignores nested matches', () => {
        host.innerHTML = `
            <div class="chart-card"><h3>Outer</h3><div class="chart-card"><h3>Nested</h3></div></div>
            <div class="chart-card"><h3>Second</h3></div>`;
        // Give the cards a rendered box so the visibility filter keeps them.
        host.querySelectorAll<HTMLElement>('.chart-card').forEach((el) => {
            el.style.width = '200px';
            el.style.height = '80px';
        });

        const cards = collectDomCards(host, '.chart-card');

        expect(cards.length).toBe(2);
        expect(cards.map((c) => c.querySelector('h3')!.textContent)).toEqual(['Outer', 'Second']);
    });

    it('rasterises a DOM card that has no canvas of its own', async () => {
        host.innerHTML = `
            <div class="chart-card" style="width:320px;height:160px;background:#ffffff">
                <h3 style="color:#6b2737">Organization Hierarchy</h3>
                <div style="width:200px;height:60px;background:#3b82f6"></div>
            </div>`;

        const card = collectDomCards(host, '.chart-card')[0];
        const snapshot = await captureElement(card, { scale: 1 });

        expect(snapshot.width).toBeGreaterThan(0);
        // More than one colour means the blue block and heading actually painted.
        expect(distinctColors(snapshot)).toBeGreaterThan(1);
    });

    it('keeps a rasterised DOM card at its on-screen aspect ratio', async () => {
        host.innerHTML = `<div class="chart-card" style="width:600px;height:200px;background:#eef2ff"></div>`;
        const card = collectDomCards(host, '.chart-card')[0];
        const box = card.getBoundingClientRect();

        const snapshot = await captureElement(card, { scale: 1 });
        const rendered = renderChartCard(
            {
                title: '',
                subtitle: '',
                stats: [],
                canvas: snapshot,
                displayWidth: box.width,
                displayHeight: box.height,
            },
            { scale: 1 }
        );

        // The 600x200 card is scaled up uniformly to clear the 240px height floor (x1.2),
        // giving 720x240 of content inside 28px margins — the 3:1 shape is preserved.
        const contentWidth = rendered.width - 28 * 2;
        const contentHeight = rendered.height - 28 * 2 - 16;
        expect(contentWidth).toBe(720);
        expect(contentHeight).toBe(240);
        expect(contentWidth / contentHeight).toBeCloseTo(box.width / box.height, 2);
    });

    it('captures a live vis-network graph together with its legend overlay', async () => {
        // Mirrors the student-graph page: a vis canvas plus an absolutely positioned legend.
        host.innerHTML = `
            <div class="sg-network-wrapper" style="position:relative;width:600px">
                <div class="sg-graph-canvas" style="width:600px;height:400px;background:#ffffff"></div>
                <div class="sg-legend" style="position:absolute;top:16px;left:16px;background:#fde68a">Legend</div>
            </div>`;

        const network = new Network(
            host.querySelector<HTMLElement>('.sg-graph-canvas')!,
            {
                nodes: new DataSet<any>([
                    { id: 'student', label: 'Student', shape: 'star', color: '#2196F3' },
                    { id: 'cat', label: 'Finance', shape: 'ellipse', color: '#4CAF50' },
                ]),
                edges: new DataSet<any>([{ id: 'e1', from: 'student', to: 'cat' }]),
            },
            { physics: { stabilization: { iterations: 20 } } }
        );
        await new Promise<void>((resolve) => network.once('afterDrawing', () => resolve()));

        const card = collectDomCards(host, '.sg-network-wrapper')[0];
        const snapshot = await captureElement(card, { scale: 1 });
        network.destroy();

        expect(card).toBeTruthy();
        // A blank white capture would report a single colour; nodes, edge and legend add more.
        expect(distinctColors(snapshot)).toBeGreaterThan(5);
    });

    it('falls back to the raw canvas title when a card has no heading', () => {
        const bare = document.createElement('canvas');
        bare.width = 100;
        bare.height = 100;
        bare.style.width = '100px';
        bare.style.height = '100px';
        host.appendChild(bare);

        const capture = captureChartCanvas(bare);

        expect(capture.title).toBe('');
        expect(capture.stats).toEqual([]);
        expect(renderChartCard(capture, {}).width).toBeGreaterThan(0);
    });
});
