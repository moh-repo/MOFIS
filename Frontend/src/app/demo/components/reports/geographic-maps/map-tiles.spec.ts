import * as L from 'leaflet';
import { environment } from 'src/environments/environment';
import { GeographicMapsComponent } from './geographic-maps.component';

/**
 * Live QA L8 (25 Sep 2026), decision D13-a: the geographic map took its background from
 * tile.openstreetmap.org, written into the component; without internet access the map
 * stayed blank and nothing said why. The tile server is now configuration, and a tile
 * that cannot be fetched shows a notice over the map.
 */
describe('Geographic map — tile server and offline notice (L8)', () => {

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    let self: any;

    beforeEach(() => {
        const proto = GeographicMapsComponent.prototype as any;
        // A detached element has no size, so Leaflet asks for no tiles during the test.
        self = { mapContainer: { nativeElement: document.createElement('div') }, tilesUnavailable: false };
        self.onTileError = proto.onTileError.bind(self);
        self.onTileLoad = proto.onTileLoad.bind(self);
        proto.initializeMap.call(self);
    });

    afterEach(() => self.map?.remove());

    const tileLayer = (): L.TileLayer => {
        let found: L.TileLayer | undefined;
        self.map.eachLayer((layer: L.Layer) => { if (layer instanceof L.TileLayer) { found = layer; } });
        return found!;
    };

    it('takes the tile server from the environment', () => {
        expect((tileLayer() as any)._url).toBe(environment.mapTiles.url);
    });

    it('says the background is unavailable when a tile fails, and stops saying so when one arrives', () => {
        tileLayer().fire('tileerror', { tile: document.createElement('img'), coords: L.point(0, 0) });
        expect(self.tilesUnavailable).toBeTrue();
        tileLayer().fire('tileload', { tile: document.createElement('img'), coords: L.point(0, 0) });
        expect(self.tilesUnavailable).toBeFalse();
    });
});
