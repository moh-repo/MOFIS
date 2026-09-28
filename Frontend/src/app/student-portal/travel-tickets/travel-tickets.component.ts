import { Component, OnInit } from '@angular/core';
import { StudentPortalApiService, PortalTravelTicket } from '../student-portal-api.service';
import { TranslationService } from '../../demo/service/translation.service';
import { ToastService } from '../../demo/service/toast.service';
import { portalEnumLabel } from '../student-portal-i18n.helper';
import { CodeListService } from '../../demo/service/code-list.service';
import { distinctValues, filterRows } from '../portal-list.helper';

@Component({
    selector: 'app-portal-travel-tickets',
    templateUrl: './travel-tickets.component.html',
    styleUrls: ['./travel-tickets.component.scss'],
    standalone: false
})
export class TravelTicketsComponent implements OnInit {

  list: PortalTravelTicket[] = [];
  loading = true;
  errorKey = '';
  selectedTicket: PortalTravelTicket | null = null;
  showDetailModal = false;

  constructor(
    private api: StudentPortalApiService,
    private translationService: TranslationService,
    private toast: ToastService,
    private codeLists: CodeListService
  ) {}

  ngOnInit(): void {
    this.api.getTravelTickets().subscribe({
      next: (data) => {
        this.list = Array.isArray(data) ? data : [];
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        if (err?.status === 404 || err?.status === 501) {
          this.list = [];
          this.errorKey = '';
        } else {
          this.errorKey = 'STUDENT_PORTAL.LOAD_TRAVEL_TICKETS_ERROR';
        }
      }
    });
  }

  /**
   * Search and type filter. Client-side, and honest here: the endpoint returns
   * every ticket, so there is no page beyond this one to miss.
   */
  search = '';
  typeFilter: string | null = null;

  get typeOptions(): string[] {
    return distinctValues(this.list, row => row.travelType);
  }

  get filteredList(): PortalTravelTicket[] {
    const byType = this.typeFilter
      ? this.list.filter(row => row.travelType === this.typeFilter)
      : this.list;
    return filterRows(byType, this.search, [
      row => this.travelTypeLabel(row.travelType),
      row => row.departureCity,
      row => row.departureCountry,
      row => row.destinationCity,
      row => row.destinationCountry,
      row => row.airline,
      row => row.flightNumber,
      row => row.ticketNumber
    ]);
  }

  get filteredEverythingOut(): boolean {
    return this.list.length > 0 && this.filteredList.length === 0;
  }

  clearFilters(): void {
    this.search = '';
    this.typeFilter = null;
  }

  travelTypeLabel(type: string | null | undefined): string {
    // The travel-type code list, as the staff screens name it (QA SP-008).
    return portalEnumLabel(this.codeLists, 'travel-type', type);
  }

  /**
   * A family member's relationship in the reader's language (live QA M17: the codes
   * SPOUSE / CHILD were shown as stored). The staff screen names them from
   * LU_FAMILY_RELATIONSHIP, which the portal cannot read; the same codes have keys
   * under STUDENT_TRAVEL_TICKETS.RELATIONSHIPS. A code with no key is shown as itself.
   */
  relationshipLabel(code: string | null | undefined): string {
    const value = (code ?? '').trim();
    if (!value) return '-';
    const key = `STUDENT_TRAVEL_TICKETS.RELATIONSHIPS.${value.toUpperCase()}`;
    const text = this.translationService.translate(key);
    return text && text !== key ? text : value;
  }

  openDetail(row: PortalTravelTicket): void {
    this.selectedTicket = row;
    this.showDetailModal = true;
  }

  closeDetail(): void {
    this.showDetailModal = false;
    this.selectedTicket = null;
  }

  /** Opens the ticket PDF in a new tab (review T3). */
  openTicketDocument(row: PortalTravelTicket): void {
    if (!row.travelTicketId) return;
    this.api.getTravelTicketDocument(row.travelTicketId).subscribe({
      next: blob => {
        const url = URL.createObjectURL(new Blob([blob], { type: 'application/pdf' }));
        window.open(url, '_blank');
        setTimeout(() => URL.revokeObjectURL(url), 60_000);
      },
      error: () => this.toast.error(this.translationService.translate('STUDENT_PORTAL.DOWNLOAD_FAILED'))
    });
  }

  getFamilyCount(row: PortalTravelTicket): number {
    return row.familyMembers?.length ?? 0;
  }

  getFamilyCountText(row: PortalTravelTicket): string {
    const n = this.getFamilyCount(row);
    return n === 0 ? '—' : (n === 1 ? '1' : `${n}`);
  }
}
