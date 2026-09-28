import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { TranslationService } from 'src/app/demo/service/translation.service';

@Component({
    selector: 'app-graphs',
    templateUrl: './graphs.component.html',
    styleUrls: ['./graphs.component.scss'],
    standalone: false
})
export class GraphsComponent {

    constructor(
        public translationService: TranslationService,
        private router: Router
    ) { }

    navigateToBarCharts() {
        this.router.navigate(['/reports/bar-charts']);
    }

    navigateToLineCharts() {
        this.router.navigate(['/reports/line-charts']);
    }

    navigateToPieCharts() {
        this.router.navigate(['/reports/pie-charts']);
    }

    navigateToHierarchicalCharts() {
        this.router.navigate(['/reports/hierarchical-charts']);
    }

    navigateToGeographicMaps() {
        this.router.navigate(['/reports/geographic-maps']);
    }

    navigateToComprehensiveDashboards() {
        this.router.navigate(['/reports/comprehensive-dashboards']);
    }
}

