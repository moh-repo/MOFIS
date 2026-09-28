import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { NzTableModule } from 'ng-zorro-antd/table';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzAlertModule } from 'ng-zorro-antd/alert';
import { NzSpinModule } from 'ng-zorro-antd/spin';
import { NzTooltipModule } from 'ng-zorro-antd/tooltip';

import { TranslatePipe } from '../../../pipes/translate.pipe';
import { TranslationService } from '../../../service/translation.service';
import { ToastService } from '../../../service/toast.service';
import { UmsUserService } from '../../../service/ums-user.service';
import { UmsAuthService } from '../../../service/ums-auth.service';
import { HasPermissionDirective } from '../../../directives/has-permission.directive';
import { AccessReview, AccessReviewItem } from '../../../api/ums';
import { extractApiErrorMessage } from '../../../service/api-error.util';

/**
 * The quarterly access review (security plan S-04).
 *
 * <p>An administrator sees the reviews they attest — the country admin their
 * own attaché's, the ministry admin the ministry's — and, per account, what it
 * held when the review opened: tier, position, roles, countries, direct
 * grants, last sign-in. Each row takes one of three answers: keep, change (the
 * administrator adjusts the account on the users screen) or remove (the
 * account is set inactive at once). A review closes once every row has an
 * answer; the nightly job flags rows left past the due date.
 */
@Component({
    selector: 'app-access-review',
    standalone: true,
    imports: [
        CommonModule, FormsModule, TranslatePipe,
        NzTableModule, NzTagModule, NzButtonModule, NzIconModule, NzInputModule, NzAlertModule, NzSpinModule,
        NzTooltipModule, HasPermissionDirective
    ],
    template: `
    <div class="card">
      <div class="flex align-items-center mb-4 flex-wrap gap-3">
        <div class="flex align-items-center gap-3 flex-1">
          <i class="pi pi-verified text-primary" style="font-size: 2.5rem"></i>
          <div>
            <h2 class="m-0 text-primary">{{ 'UMS.ACCESS_REVIEW.TITLE' | translate }}</h2>
            <p class="m-0 text-500">{{ 'UMS.ACCESS_REVIEW.SUBTITLE' | translate }}</p>
          </div>
        </div>
        <button nz-button (click)="loadReviews()" [nzLoading]="loading"><span nz-icon nzType="reload"></span>{{ 'COMMON.REFRESH' | translate }}</button>
        @if (auth.isMinistryLevel) {
          <button nz-button nzType="primary" *hasPermission="'ACCESS_REVIEW.EDIT'" (click)="openNow()" [nzLoading]="opening">
            <span nz-icon nzType="plus"></span>{{ 'UMS.ACCESS_REVIEW.OPEN_NOW' | translate }}
          </button>
        }
      </div>

      <nz-table #reviewsTable [nzData]="reviews" [nzLoading]="loading" nzSize="small" [nzPageSize]="10">
        <thead>
          <tr>
            <th>{{ 'UMS.ACCESS_REVIEW.PERIOD' | translate }}</th>
            <th>{{ 'UMS.ACCESS_REVIEW.SCOPE' | translate }}</th>
            <th>{{ 'UMS.ACCESS_REVIEW.STATUS' | translate }}</th>
            <th>{{ 'UMS.ACCESS_REVIEW.DUE' | translate }}</th>
            <th>{{ 'UMS.ACCESS_REVIEW.PROGRESS' | translate }}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          @for (r of reviewsTable.data; track r.id) {
            <tr [class.font-bold]="selected?.id === r.id">
              <td>{{ r.period }}</td>
              <td>{{ r.countryName || ('UMS.ACCESS_REVIEW.MINISTRY' | translate) }}</td>
              <td><nz-tag [nzColor]="r.status === 'OPEN' ? (isOverdue(r) ? 'volcano' : 'blue') : 'green'">{{ ('UMS.ACCESS_REVIEW.STATUS_' + r.status) | translate }}</nz-tag></td>
              <td>{{ r.dueAt | date:'yyyy-MM-dd' }}</td>
              <td>{{ r.total - r.undecided }} / {{ r.total }}</td>
              <td><button nz-button nzType="link" nzSize="small" (click)="select(r)"><span nz-icon nzType="eye"></span>{{ 'UMS.ACCESS_REVIEW.OPEN' | translate }}</button></td>
            </tr>
          }
        </tbody>
      </nz-table>
      @if (!loading && reviews.length === 0) {
        <nz-alert nzType="info" [nzMessage]="'UMS.ACCESS_REVIEW.NONE' | translate" class="mt-3"></nz-alert>
      }
    </div>

    @if (selected) {
      <div class="card mt-3">
        <div class="flex align-items-center mb-3 flex-wrap gap-3">
          <h3 class="m-0 flex-1">
            {{ selected.period }} · {{ selected.countryName || ('UMS.ACCESS_REVIEW.MINISTRY' | translate) }}
            <nz-tag class="ml-2" [nzColor]="selected.status === 'OPEN' ? 'blue' : 'green'">{{ ('UMS.ACCESS_REVIEW.STATUS_' + selected.status) | translate }}</nz-tag>
          </h3>
          @if (selected.status === 'OPEN') {
            <button nz-button nzType="primary" *hasPermission="'ACCESS_REVIEW.EDIT'" [disabled]="undecided > 0" (click)="close()" [nzLoading]="closing">
              <span nz-icon nzType="check-circle"></span>{{ 'UMS.ACCESS_REVIEW.CLOSE' | translate }}
            </button>
          }
        </div>
        @if (isOverdue(selected) && undecided > 0) {
          <nz-alert nzType="warning" [nzMessage]="'UMS.ACCESS_REVIEW.OVERDUE' | translate" class="mb-3"></nz-alert>
        }
        <p class="text-500">{{ 'UMS.ACCESS_REVIEW.INTRO' | translate }}</p>

        <nz-table #itemsTable [nzData]="items" [nzLoading]="loadingItems" nzSize="small" [nzPageSize]="25" [nzScroll]="{ x: '1100px' }">
          <thead>
            <tr>
              <th>{{ 'UMS.USERS.USERNAME' | translate }}</th>
              <th>{{ 'UMS.USERS.NAME' | translate }}</th>
              <th>{{ 'UMS.ACCESS_REVIEW.TIER' | translate }}</th>
              <th>{{ 'UMS.ACCESS_REVIEW.ROLES' | translate }}</th>
              <th>{{ 'UMS.ACCESS_REVIEW.COUNTRIES' | translate }}</th>
              <th>{{ 'UMS.USERS.LAST_LOGIN' | translate }}</th>
              <th>{{ 'UMS.ACCESS_REVIEW.GRANTS' | translate }}</th>
              <th>{{ 'UMS.ACCESS_REVIEW.DECISION' | translate }}</th>
            </tr>
          </thead>
          <tbody>
            @for (i of itemsTable.data; track i.id) {
              <tr [class.bg-orange-50]="i.overdue">
                <td>{{ i.snapshot.userName }}</td>
                <td>{{ i.snapshot.fullName }}<br><small class="text-500">{{ i.snapshot.positionCode || '' }}</small></td>
                <td><nz-tag>{{ i.snapshot.tier }}</nz-tag></td>
                <td>@for (r of i.snapshot.roles; track r) { <nz-tag>{{ r }}</nz-tag> }</td>
                <td>{{ i.snapshot.countries.join(', ') }}</td>
                <td>{{ i.snapshot.lastLoginAt ? (i.snapshot.lastLoginAt | date:'yyyy-MM-dd') : ('UMS.USERS.NEVER' | translate) }}</td>
                <td>@for (g of i.snapshot.directGrants; track g) { <nz-tag nzColor="gold">{{ g }}</nz-tag> }</td>
                <td style="min-width: 16rem">
                  @if (i.decision) {
                    <nz-tag [nzColor]="i.decision === 'KEEP' ? 'green' : i.decision === 'CHANGE' ? 'orange' : 'red'">
                      {{ ('UMS.ACCESS_REVIEW.DECISION_' + i.decision) | translate }}
                    </nz-tag>
                    @if (i.note) { <small class="text-500 block">{{ i.note }}</small> }
                  } @else if (selected.status === 'OPEN') {
                    <ng-container *hasPermission="'ACCESS_REVIEW.EDIT'">
                      <input nz-input nzSize="small" class="mb-1" [(ngModel)]="notes[i.id]" [placeholder]="'UMS.ACCESS_REVIEW.NOTE' | translate" maxlength="500">
                      <div class="flex gap-1">
                        <button nz-button nzSize="small" (click)="decide(i, 'KEEP')" [nzLoading]="busy === i.id">{{ 'UMS.ACCESS_REVIEW.DECISION_KEEP' | translate }}</button>
                        <button nz-button nzSize="small" (click)="decide(i, 'CHANGE')" [nzLoading]="busy === i.id">{{ 'UMS.ACCESS_REVIEW.DECISION_CHANGE' | translate }}</button>
                        <button nz-button nzSize="small" nzDanger (click)="decide(i, 'REMOVE')" [nzLoading]="busy === i.id"
                          [disabled]="i.userId === auth.currentUserValue?.id"
                          [nz-tooltip]="'UMS.ACCESS_REVIEW.REMOVE_HINT' | translate">{{ 'UMS.ACCESS_REVIEW.DECISION_REMOVE' | translate }}</button>
                      </div>
                    </ng-container>
                  } @else {
                    <span class="text-500">—</span>
                  }
                </td>
              </tr>
            }
          </tbody>
        </nz-table>
      </div>
    }
    `
})
export class AccessReviewComponent implements OnInit {

    private readonly userService = inject(UmsUserService);
    readonly auth = inject(UmsAuthService);
    private readonly translation = inject(TranslationService);
    private readonly toast = inject(ToastService);

    reviews: AccessReview[] = [];
    selected: AccessReview | null = null;
    items: AccessReviewItem[] = [];
    notes: { [itemId: number]: string } = {};
    loading = false;
    loadingItems = false;
    opening = false;
    closing = false;
    busy: number | null = null;

    ngOnInit(): void {
        this.loadReviews();
    }

    get undecided(): number {
        return this.items.filter(i => !i.decision).length;
    }

    isOverdue(r: AccessReview): boolean {
        return r.status === 'OPEN' && new Date(r.dueAt).getTime() < Date.now();
    }

    loadReviews(): void {
        this.loading = true;
        this.userService.listAccessReviews().subscribe({
            next: list => {
                this.reviews = list;
                this.loading = false;
                if (this.selected) {
                    const again = list.find(r => r.id === this.selected!.id);
                    if (again) { this.selected = again; }
                }
            },
            error: () => { this.loading = false; this.toast.error(this.translation.translate('UMS.ACCESS_REVIEW.LOAD_FAILED')); }
        });
    }

    select(r: AccessReview): void {
        this.selected = r;
        this.loadingItems = true;
        this.userService.getAccessReview(r.id).subscribe({
            next: res => { this.selected = res.review; this.items = res.items; this.loadingItems = false; },
            error: () => { this.loadingItems = false; this.toast.error(this.translation.translate('UMS.ACCESS_REVIEW.LOAD_FAILED')); }
        });
    }

    decide(item: AccessReviewItem, decision: 'KEEP' | 'CHANGE' | 'REMOVE'): void {
        if (!this.selected) { return; }
        if (decision === 'REMOVE' && !confirm(this.translation.translate('UMS.ACCESS_REVIEW.REMOVE_CONFIRM'))) { return; }
        this.busy = item.id;
        this.userService.decideAccessReviewItem(this.selected.id, item.id, decision, this.notes[item.id] || null).subscribe({
            next: updated => {
                this.busy = null;
                this.items = this.items.map(i => i.id === updated.id ? updated : i);
                if (this.selected) { this.selected = { ...this.selected, undecided: this.undecided }; }
                this.reviews = this.reviews.map(r => r.id === this.selected?.id ? this.selected! : r);
            },
            error: err => {
                this.busy = null;
                this.toast.error(extractApiErrorMessage(err) || this.translation.translate('UMS.ACCESS_REVIEW.DECIDE_FAILED'));
            }
        });
    }

    close(): void {
        if (!this.selected) { return; }
        this.closing = true;
        this.userService.closeAccessReview(this.selected.id).subscribe({
            next: r => { this.closing = false; this.selected = r; this.toast.success(this.translation.translate('UMS.ACCESS_REVIEW.CLOSED')); this.loadReviews(); },
            error: err => { this.closing = false; this.toast.error(extractApiErrorMessage(err) || this.translation.translate('UMS.ACCESS_REVIEW.CLOSE_FAILED')); }
        });
    }

    openNow(): void {
        this.opening = true;
        this.userService.openAccessReviews().subscribe({
            next: res => {
                this.opening = false;
                this.toast.success(`${this.translation.translate('UMS.ACCESS_REVIEW.OPENED')}: ${res.period} (${res.opened})`);
                this.loadReviews();
            },
            error: err => { this.opening = false; this.toast.error(extractApiErrorMessage(err) || this.translation.translate('UMS.ACCESS_REVIEW.OPEN_FAILED')); }
        });
    }
}
