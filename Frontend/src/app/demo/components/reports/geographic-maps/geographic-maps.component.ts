import { Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { TranslationService } from 'src/app/demo/service/translation.service';
import { ChartDataService } from 'src/app/demo/service/chart-data.service';
import { ChartPrintService } from 'src/app/demo/service/chart-print.service';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import * as L from 'leaflet';
import { changesOnly } from '../../../service/reload-streams.util';
import { environment } from 'src/environments/environment';

const PRINT_AREA_ID = 'geographic-maps-print-area';

/** The Leaflet map and its details list are DOM, so exports rasterise those panels. */
const EXPORT_OPTIONS = { domCardSelector: '.stats-grid, .map-panel, .details-panel' };

interface GeoBreakdownResponse {
    name: string;
    value: number;
    children?: GeoBreakdownResponse[];
}

interface GeoLocationResponse {
    country: string;
    latitude: number;
    longitude: number;
    studentCount: number;
    governorates: GeoBreakdownResponse[];
}

interface GeoSummary {
    totalStudents: number;
    countryCount: number;
    topCountry?: GeoLocationResponse;
}

@Component({
    selector: 'app-geographic-maps',
    templateUrl: './geographic-maps.component.html',
    styleUrls: ['./geographic-maps.component.scss'],
    providers: [MessageService],
    standalone: false
})
export class GeographicMapsComponent implements OnInit, OnDestroy {

    @ViewChild('mapContainer', { static: true }) mapContainer!: ElementRef<HTMLDivElement>;

    map?: L.Map;
    markerLayer?: L.LayerGroup;

    locations: GeoLocationResponse[] = [];
    filteredGovernorates: GeoBreakdownResponse[] = [];
    filteredMunicipalities: GeoBreakdownResponse[] = [];

    summary: GeoSummary = { totalStudents: 0, countryCount: 0 };

    selectedCountry?: GeoLocationResponse;
    selectedGovernorate?: GeoBreakdownResponse;

    searchTerm = '';

    /**
     * The map background could not be fetched (live QA L8: without internet the
     * OpenStreetMap tiles never arrive and the map stayed blank with no word why).
     * The markers and figures come from this server and are unaffected.
     */
    tilesUnavailable = false;
    loading = false;

    private readonly destroy$ = new Subject<void>();

    constructor(
        private chartDataService: ChartDataService,
        private chartPrintService: ChartPrintService,
        private router: Router,
        private messageService: MessageService,
        public translationService: TranslationService
    ) {}

    ngOnInit(): void {
        this.initializeMap();
        this.loadGeographicData();
        this.chartDataService.selectedCountryId$.pipe(changesOnly(), takeUntil(this.destroy$)).subscribe(() => this.loadGeographicData());
    }

    ngOnDestroy(): void {
        this.destroy$.next();
        this.destroy$.complete();
        this.map?.remove();
    }

    get filteredCountries(): GeoLocationResponse[] {
        if (!this.searchTerm.trim()) return this.locations;
        const term = this.searchTerm.toLowerCase();
        return this.locations.filter(l => l.country.toLowerCase().includes(term));
    }

    initializeMap(): void {
        if (this.map) return;

        this.map = L.map(this.mapContainer.nativeElement, {
            center: [26.3351, 17.2283],
            zoom: 3,
            worldCopyJump: true,
            zoomControl: true
        });

        // The tile server is configuration (D13-a): a site without internet access sets
        // its own in environment*.ts.
        const tiles = environment.mapTiles;
        L.tileLayer(tiles.url, {
            maxZoom: tiles.maxZoom,
            // Request tiles with CORS so exporting the map does not taint the capture canvas.
            crossOrigin: true,
            attribution: tiles.attribution
        })
            .on('tileerror', () => this.onTileError())
            .on('tileload', () => this.onTileLoad())
            .addTo(this.map);

        this.markerLayer = L.layerGroup().addTo(this.map);
    }

    /** A tile failed: say the background is unavailable (once, not per tile). */
    onTileError(): void {
        this.tilesUnavailable = true;
    }

    /** A tile arrived after all (the connection came back): the notice goes. */
    onTileLoad(): void {
        this.tilesUnavailable = false;
    }

    loadGeographicData(): void {
        this.loading = true;
        this.chartDataService.getGeographicDistribution().subscribe({
            next: (data) => {
                this.locations = data || [];
                this.summary.totalStudents = this.locations.reduce((sum, l) => sum + (l.studentCount || 0), 0);
                this.summary.countryCount = this.locations.length;
                this.summary.topCountry = this.locations
                    .slice()
                    .sort((a, b) => (b.studentCount || 0) - (a.studentCount || 0))[0];
                this.updateMapMarkers();
                this.selectCountry(this.summary.topCountry || this.locations[0]);
                this.loading = false;
            },
            error: (error) => {
                console.error('Error loading geographic distribution', error);
                this.showError(this.translationService.translate('GEOGRAPHIC_MAPS.ERRORS.LOAD'));
                this.loading = false;
            }
        });
    }

    updateMapMarkers(): void {
        if (!this.markerLayer) return;
        this.markerLayer.clearLayers();

        const maxCount = this.locations.reduce((max, l) => Math.max(max, l.studentCount || 0), 0);
        const minRadius = 10;
        const maxRadius = 38;

        this.locations.forEach(location => {
            const lat = location.latitude ?? 0;
            const lng = location.longitude ?? 0;
            if (lat === 0 && lng === 0) return;

            const ratio = maxCount > 0 ? (location.studentCount || 0) / maxCount : 0;
            const radius = minRadius + ratio * (maxRadius - minRadius);
            const color = this.getMarkerColor(ratio);

            const pct = this.summary.totalStudents > 0
                ? ((location.studentCount / this.summary.totalStudents) * 100).toFixed(1)
                : '0.0';
            const govCount = location.governorates?.length ?? 0;
            const govLine = govCount > 0
                ? `<div class="map-popup__row"><span>${this.translationService.translate('GEOGRAPHIC_MAPS.POPUP.GOVERNORATES')}</span><strong>${govCount}</strong></div>`
                : '';

            const marker = L.circleMarker([lat, lng], {
                radius,
                color,
                fillColor: color,
                fillOpacity: 0.7,
                weight: 2
            });

            marker.bindPopup(`
                <div class="map-popup">
                    <div class="map-popup__country">${location.country}</div>
                    <div class="map-popup__row">
                        <span>${this.translationService.translate('GEOGRAPHIC_MAPS.POPUP.STUDENTS')}</span>
                        <strong>${this.formatNumber(location.studentCount)}</strong>
                    </div>
                    <div class="map-popup__row">
                        <span>${this.translationService.translate('GEOGRAPHIC_MAPS.POPUP.SHARE')}</span>
                        <strong>${pct}%</strong>
                    </div>
                    ${govLine}
                </div>
            `, { maxWidth: 220 });

            marker.on('click', () => this.selectCountry(location, false));
            marker.addTo(this.markerLayer!);
        });

        const layers = this.markerLayer!.getLayers();
        if (layers.length > 0 && this.map) {
            const fg = L.featureGroup(layers as L.Layer[]);
            const bounds = fg.getBounds();
            if (bounds.isValid()) {
                this.map.fitBounds(bounds, { padding: [40, 40], animate: false });
            }
        }
    }

    selectCountry(location?: GeoLocationResponse, moveMap = true): void {
        this.selectedCountry = location;
        this.selectedGovernorate = undefined;
        this.filteredGovernorates = location?.governorates || [];
        this.filteredMunicipalities = [];

        if (location && moveMap && this.map && location.latitude && location.longitude) {
            this.map.flyTo([location.latitude, location.longitude], 5, { duration: 0.75 });
        }
    }

    selectGovernorate(governorate: GeoBreakdownResponse): void {
        this.selectedGovernorate = governorate;
        this.filteredMunicipalities = governorate.children || [];
    }

    getStudentPercentage(count: number): number {
        return this.summary.totalStudents > 0
            ? Math.round((count / this.summary.totalStudents) * 1000) / 10
            : 0;
    }

    private getMarkerColor(ratio: number): string {
        if (ratio < 0.33) return '#22c55e';
        if (ratio < 0.66) return '#f59e0b';
        return '#ef4444';
    }

    refresh(): void { this.loadGeographicData(); }
    goBack(): void { this.router.navigate(['/reports/graphs']); }

    printCharts(): void {
        void this.chartPrintService.printChartsPdf(PRINT_AREA_ID, this.pageTitle(), { ...EXPORT_OPTIONS });
    }

    previewChartsPdf(): void {
        void this.chartPrintService.previewChartsPdf(PRINT_AREA_ID, this.pageTitle(), {
            ...EXPORT_OPTIONS,
            filename: 'geographic-maps.pdf'
        });
    }

    downloadChartsPdf(): void {
        void this.chartPrintService.downloadChartsPdf(PRINT_AREA_ID, this.pageTitle(), {
            ...EXPORT_OPTIONS,
            filename: 'geographic-maps.pdf'
        });
    }

    downloadChartImages(): void {
        void this.chartPrintService.downloadChartImages(PRINT_AREA_ID, this.pageTitle(), {
            ...EXPORT_OPTIONS,
            filename: 'geographic-maps'
        });
    }

    private pageTitle(): string {
        return this.translationService.translate('GRAPHS.GEOGRAPHIC_MAPS');
    }

    trackByCountry(_index: number, item: GeoLocationResponse): string { return item.country; }
    trackByName(_index: number, item: GeoBreakdownResponse): string { return item.name; }

    formatNumber(value?: number): string { return (value ?? 0).toLocaleString(); }

    showError(message: string): void {
        this.messageService.add({
            severity: 'error',
            summary: this.translationService.translate('COMMON.ERROR'),
            detail: message
        });
    }

    isRTL(): boolean { return this.translationService.isRTL(); }
}
