import { Component, OnInit } from '@angular/core';
import { StudentPortalApiService, StudentYearResultDto } from '../student-portal-api.service';
import { filterRows } from '../portal-list.helper';
import { YearOutcome, yearOutcome } from '../../demo/api/student-year-result';

@Component({
    selector: 'app-student-portal-year-results',
    templateUrl: './year-results.component.html',
    styleUrls: ['./year-results.component.scss'],
    standalone: false
})
export class YearResultsComponent implements OnInit {
  results: StudentYearResultDto[] = [];
  loading = true;
  errorKey = '';

  /**
   * Search and outcome filter. The endpoint returns the whole transcript, so
   * both are applied here.
   */
  search = '';
  outcomeFilter: 'passed' | 'failed' | null = null;

  get filteredResults(): StudentYearResultDto[] {
    const byOutcome = this.outcomeFilter === null
      ? this.results
      // A placeholder year is neither passed nor failed (FIX-PLAN P5-8).
      : this.results.filter(r => yearOutcome(r) === this.outcomeFilter);
    return filterRows(byOutcome, this.search, [
      r => r.yearNumber,
      r => r.academicYear,
      r => r.grade,
      r => r.notes
    ]);
  }

  get filteredEverythingOut(): boolean {
    return this.results.length > 0 && this.filteredResults.length === 0;
  }

  /** Only worth offering the filter when the transcript actually has both. */
  get hasBothOutcomes(): boolean {
    return this.results.some(r => yearOutcome(r) === 'passed') && this.results.some(r => yearOutcome(r) === 'failed');
  }

  outcome(r: StudentYearResultDto): YearOutcome {
    return yearOutcome(r);
  }

  outcomeColor(r: StudentYearResultDto): string {
    return { passed: 'success', failed: 'error', 'not-recorded': 'default' }[yearOutcome(r)];
  }

  outcomeIcon(r: StudentYearResultDto): string {
    return { passed: 'check-circle', failed: 'close-circle', 'not-recorded': 'minus-circle' }[yearOutcome(r)];
  }

  outcomeKey(r: StudentYearResultDto): string {
    return { passed: 'STUDENT_PORTAL.PASSED_YES', failed: 'STUDENT_PORTAL.PASSED_NO',
      'not-recorded': 'STUDENT_PORTAL.NOT_RECORDED' }[yearOutcome(r)];
  }

  clearFilters(): void {
    this.search = '';
    this.outcomeFilter = null;
  }

  constructor(private api: StudentPortalApiService) {}

  ngOnInit(): void {
    this.api.getYearResults().subscribe({
      next: r => { this.results = r; this.loading = false; },
      error: () => {
        this.errorKey = 'STUDENT_PORTAL.LOAD_YEAR_RESULTS_ERROR';
        this.loading = false;
      }
    });
  }
}
