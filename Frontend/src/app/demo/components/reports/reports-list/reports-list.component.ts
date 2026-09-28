import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { TranslationService } from 'src/app/demo/service/translation.service';
import { ReportHubCard, hubCardDescription, hubCardName } from 'src/app/demo/service/report-hub-permission.service';
import {
    ReportCatalogCard,
    ReportCatalogGroup,
    ReportCatalogService,
    normaliseForSearch
} from 'src/app/demo/service/report-catalog.service';

/**
 * The reports landing page: the hubs the reader may open, and under them every
 * report in the catalogue, searchable.
 *
 * The page used to stop at the hub cards (review item P1), so finding one report
 * out of roughly fifty meant knowing which of the eight hubs it had been filed
 * under and opening them one at a time. The full list comes from the same
 * catalogue the hubs are built from, filtered by the same permissions, so nothing
 * appears here that the reader could not already reach.
 */
@Component({
    selector: 'app-reports-list',
    templateUrl: './reports-list.component.html',
    styleUrls: ['./reports-list.component.scss'],
    standalone: false
})
export class ReportsListComponent implements OnInit, OnDestroy {

    visibleCategories: ReportHubCard[] = [];
    /** Every report the reader may open, grouped by hub. */
    groups: ReportCatalogGroup[] = [];
    /** `groups` narrowed to what matches `search`. */
    filteredGroups: ReportCatalogGroup[] = [];
    search = '';
    currentLanguage = 'en';
    loading = true;

    private readonly subscriptions = new Subscription();
    private catalogueSub?: Subscription;

    constructor(
        public translationService: TranslationService,
        private catalog: ReportCatalogService
    ) { }

    ngOnInit(): void {
        this.currentLanguage = this.translationService.getCurrentLanguage();
        this.subscriptions.add(
            this.translationService.currentLanguage$.subscribe(lang => { this.currentLanguage = lang; })
        );
        this.loadCatalogue();
    }

    ngOnDestroy(): void {
        this.catalogueSub?.unsubscribe();
        this.subscriptions.unsubscribe();
    }

    private loadCatalogue(): void {
        this.catalogueSub?.unsubscribe();
        this.catalogueSub = new Subscription();
        this.catalogueSub.add(
            this.catalog.landingCards().subscribe(cards => {
                this.visibleCategories = cards;
                this.loading = false;
            })
        );
        // Re-emits on a language change, so the list is named and searched in the
        // language on screen without a second request.
        this.catalogueSub.add(
            this.catalog.allReports(this.translationService.currentLanguage$).subscribe(groups => {
                this.groups = groups;
                this.applySearch();
            })
        );
    }

    /** True when the catalogue could not be loaded, as opposed to being empty. */
    get loadFailed(): boolean {
        return this.catalog.loadFailed;
    }

    get totalReports(): number {
        return this.groups.reduce((sum, group) => sum + group.reports.length, 0);
    }

    get matchCount(): number {
        return this.filteredGroups.reduce((sum, group) => sum + group.reports.length, 0);
    }

    /**
     * Matches the typed text against a report's name in both languages, its
     * description and its hub's name — all normalised, so Arabic spelled with or
     * without hamza and diacritics finds the same rows.
     */
    applySearch(): void {
        const needle = normaliseForSearch(this.search);
        if (!needle) {
            this.filteredGroups = this.groups;
            return;
        }
        const terms = needle.split(' ');
        this.filteredGroups = this.groups
            .map(group => ({
                ...group,
                reports: group.reports.filter(report => {
                    const haystack = normaliseForSearch(
                        [report.title, report.titleAr, report.description, group.hubTitle].join(' '));
                    return terms.every(term => haystack.includes(term));
                })
            }))
            .filter(group => group.reports.length > 0);
    }

    clearSearch(): void {
        this.search = '';
        this.applySearch();
    }

    retry(): void {
        this.loading = true;
        this.catalog.reload();
        this.loadCatalogue();
    }

    name(card: ReportHubCard): string {
        return hubCardName(card, this.currentLanguage);
    }

    description(card: ReportHubCard): string {
        return hubCardDescription(card, this.currentLanguage);
    }

    /** A report listed but not yet built is shown greyed, as the hub pages show it. */
    isAvailable(report: ReportCatalogCard): boolean {
        return report.available;
    }
}
