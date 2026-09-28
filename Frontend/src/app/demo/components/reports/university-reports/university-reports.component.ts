import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { TranslationService } from 'src/app/demo/service/translation.service';
import { ReportCatalogCard, ReportCatalogService } from 'src/app/demo/service/report-catalog.service';

/**
 * A report hub. Its cards are the catalogue rows (`UMS_Reports`) whose parent
 * is the hub at this route, in catalogue order, named in the reader's language
 * and filtered by what the reader may open. Nothing about the cards is listed
 * here.
 */
@Component({
    selector: 'app-university-reports',
    templateUrl: './university-reports.component.html',
    styleUrls: ['./university-reports.component.scss'],
    standalone: false
})
export class UniversityReportsComponent implements OnInit, OnDestroy {

    reportTypes: ReportCatalogCard[] = [];
    private subscription?: Subscription;

    constructor(
        private router: Router,
        public translationService: TranslationService,
        private catalog: ReportCatalogService
    ) { }

    ngOnInit(): void {
        this.subscription = this.catalog
            .hubCards(this.router.url, this.translationService.currentLanguage$)
            .subscribe(cards => { this.reportTypes = cards; });
    }

    ngOnDestroy(): void {
        this.subscription?.unsubscribe();
    }

    navigateToReport(report: ReportCatalogCard): void {
        if (report.route && report.implemented) {
            this.router.navigate([report.route], { queryParams: report.queryParams ?? undefined });
        }
    }
}
