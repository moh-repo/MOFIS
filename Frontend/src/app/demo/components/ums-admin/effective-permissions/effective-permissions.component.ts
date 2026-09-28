import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzTabsModule } from 'ng-zorro-antd/tabs';
import { NzTableModule } from 'ng-zorro-antd/table';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { NzDescriptionsModule } from 'ng-zorro-antd/descriptions';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzAlertModule } from 'ng-zorro-antd/alert';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzSpinModule } from 'ng-zorro-antd/spin';

import { TranslatePipe } from '../../../pipes/translate.pipe';
import { TranslationService } from '../../../service/translation.service';
import { CodeListService } from '../../../service/code-list.service';
import { ToastService } from '../../../service/toast.service';
import { UmsUserService } from '../../../service/ums-user.service';
import { UmsAuthService } from '../../../service/ums-auth.service';
import {
    DirectGrantException, EffectivePermissionDiff, EffectivePermissionReport, SeparationOfDutiesReport, UmsUserSummary
} from '../../../api/ums';

/**
 * Security plan R-13 (6 Sep 2026): what one account can do and why.
 *
 * The resolved permission set with its provenance (role, group, direct grant),
 * the tier and countries, the live exceptions (R-14) and the separation-of-
 * duties picture (R-15), and the R-12 cutover list — the codes an administrator
 * reached through ROLE_ADMIN yesterday that the tier bundle does not carry.
 * Read-only; the server scopes the accounts to those the caller may administer.
 */
@Component({
    selector: 'app-effective-permissions',
    standalone: true,
    imports: [
        CommonModule, FormsModule, TranslatePipe,
        NzSelectModule, NzTabsModule, NzTableModule, NzTagModule, NzDescriptionsModule,
        NzButtonModule, NzIconModule, NzAlertModule, NzInputModule, NzSpinModule
    ],
    template: `
    <div class="card">
      <div class="flex align-items-center mb-4 flex-wrap gap-3">
        <div class="flex align-items-center gap-3 flex-1">
          <i class="pi pi-shield text-primary" style="font-size: 2.5rem"></i>
          <div>
            <h2 class="m-0 text-primary">{{ 'UMS.USERS.EFF_TITLE' | translate }}</h2>
            <p class="m-0 text-500">{{ 'UMS.USERS.EFF_SUBTITLE' | translate }}</p>
          </div>
        </div>
      </div>

      <div class="flex flex-wrap gap-3 mb-4 align-items-end">
        <div style="min-width: 18rem">
          <label class="block text-sm text-500 mb-1">{{ 'UMS.USERS.EFF_SELECT_USER' | translate }}</label>
          <nz-select style="width: 100%" nzShowSearch [(ngModel)]="selectedUserId" (ngModelChange)="load()"
            [nzPlaceHolder]="'UMS.USERS.EFF_SELECT_USER' | translate">
            <nz-option [nzValue]="0" [nzLabel]="'UMS.USERS.EFF_ME' | translate"></nz-option>
            @for (u of users; track u.id) {
              <nz-option [nzValue]="u.id" [nzLabel]="u.userName + (u.firstName || u.lastName ? ' — ' + u.firstName + ' ' + u.lastName : '')"></nz-option>
            }
          </nz-select>
        </div>
        <div style="min-width: 18rem">
          <label class="block text-sm text-500 mb-1">{{ 'UMS.USERS.EFF_COMPARE_WITH' | translate }}</label>
          <nz-select style="width: 100%" nzShowSearch nzAllowClear [(ngModel)]="compareUserId" (ngModelChange)="loadDiff()"
            [nzPlaceHolder]="'UMS.USERS.EFF_COMPARE_WITH' | translate">
            @for (u of users; track u.id) {
              <nz-option [nzValue]="u.id" [nzLabel]="u.userName"></nz-option>
            }
          </nz-select>
        </div>
        <button nz-button (click)="load()" [nzLoading]="loading"><span nz-icon nzType="reload"></span>{{ 'COMMON.REFRESH' | translate }}</button>
      </div>

      <nz-spin [nzSpinning]="loading">
      @if (report) {
        <nz-descriptions nzBordered [nzColumn]="3" nzSize="small" class="mb-4">
          <nz-descriptions-item [nzTitle]="'UMS.USERS.USERNAME' | translate">{{ report.userName }} <span class="text-500">{{ report.fullName }}</span></nz-descriptions-item>
          <nz-descriptions-item [nzTitle]="'UMS.USERS.EFF_TIER' | translate">
            <nz-tag [nzColor]="tierColor(report.adminTier)">{{ ('UMS.USERS.TIER_' + report.adminTier) | translate }}</nz-tag>
          </nz-descriptions-item>
          <nz-descriptions-item [nzTitle]="'UMS.USERS.POSITION' | translate">{{ positionName(report.positionCode) || ('COMMON.NOT_AVAILABLE' | translate) }}</nz-descriptions-item>
          <nz-descriptions-item [nzTitle]="'UMS.USERS.EFF_COUNTRIES' | translate" [nzSpan]="3">
            @for (c of report.countries; track c.id) { <nz-tag>{{ countryName(c) }}</nz-tag> }
            @if (!report.countries.length) { <span class="text-500">{{ 'UMS.USERS.AWAITING_ATTACHE' | translate }}</span> }
          </nz-descriptions-item>
          <nz-descriptions-item [nzTitle]="'UMS.USERS.EFF_ROLES' | translate" [nzSpan]="2">
            @for (r of report.roles; track r.id + r.via) {
              <nz-tag [nzColor]="r.tierRole ? 'geekblue' : 'default'">{{ localName(r.name, r.nameAr) }} <span class="text-500">· {{ sourceLabel(r.via) }}</span></nz-tag>
            }
            @if (!report.roles.length) { <span class="text-500">{{ 'UMS.USERS.EFF_NONE' | translate }}</span> }
          </nz-descriptions-item>
          <nz-descriptions-item [nzTitle]="'UMS.USERS.EFF_GROUPS' | translate">
            @for (g of report.groups; track g.id) { <nz-tag>{{ localName(g.name, g.nameAr) }}</nz-tag> }
            @if (!report.groups.length) { <span class="text-500">{{ 'UMS.USERS.EFF_NONE' | translate }}</span> }
          </nz-descriptions-item>
        </nz-descriptions>

        @for (note of report.notes; track note) {
          <nz-alert nzType="info" [nzMessage]="('UMS.USERS.EFF_NOTE_' + note) | translate" nzShowIcon class="mb-2"></nz-alert>
        }
        @if (report.lostVersusLegacyAdmin.length) {
          <nz-alert nzType="warning" nzShowIcon class="mb-3"
            [nzMessage]="'UMS.USERS.EFF_LOST_LEGACY' | translate"
            [nzDescription]="lostDescription"></nz-alert>
          <ng-template #lostDescription>
            <p class="m-0 mb-2 text-sm">{{ 'UMS.USERS.EFF_LOST_LEGACY_HINT' | translate }}</p>
            @for (code of report.lostVersusLegacyAdmin; track code) { <nz-tag>{{ code }}</nz-tag> }
          </ng-template>
        }

        <nz-tabs>
          <nz-tab [nzTitle]="('UMS.USERS.EFF_PERMISSIONS' | translate) + ' (' + report.permissions.length + ')'">
            <input nz-input [(ngModel)]="filter" [placeholder]="'UMS.USERS.EFF_FILTER' | translate" class="mb-2" style="max-width: 20rem" />
            <nz-table #permTable [nzData]="filteredPermissions" nzSize="small" [nzPageSize]="25" [nzShowSizeChanger]="true">
              <thead>
                <tr>
                  <th>{{ 'UMS.USERS.PERMISSIONS' | translate }}</th>
                  <th>{{ 'UMS.ROLES.DESCRIPTION' | translate }}</th>
                  <th>{{ 'UMS.USERS.EFF_SOURCES' | translate }}</th>
                </tr>
              </thead>
              <tbody>
                @for (p of permTable.data; track p.code) {
                  <tr>
                    <td><code>{{ p.code }}</code>
                      @if (!p.enforced) { <nz-tag class="ml-1">{{ 'UMS.USERS.EFF_MENU_ONLY' | translate }}</nz-tag> }
                    </td>
                    <td class="text-500">{{ permissionText(p) }}</td>
                    <td>@for (s of p.sources; track s) { <nz-tag [nzColor]="sourceColor(s)">{{ sourceLabel(s) }}</nz-tag> }</td>
                  </tr>
                }
              </tbody>
            </nz-table>
          </nz-tab>

          <nz-tab [nzTitle]="('UMS.USERS.EFF_DIRECT_GRANTS' | translate) + ' (' + report.directGrants.length + ')'">
            <nz-table #grantTable [nzData]="report.directGrants" nzSize="small" [nzShowPagination]="false">
              <thead>
                <tr>
                  <th>{{ 'UMS.USERS.PERMISSIONS' | translate }}</th>
                  <th>{{ 'UMS.USERS.EFF_REASON' | translate }}</th>
                  <th>{{ 'UMS.USERS.EFF_EXPIRES' | translate }}</th>
                  <th>{{ 'UMS.USERS.STATUS' | translate }}</th>
                </tr>
              </thead>
              <tbody>
                @for (g of grantTable.data; track g.permissionId) {
                  <tr>
                    <td><code>{{ g.code }}</code></td>
                    <td>{{ g.reason || '' }}</td>
                    <td>{{ g.expiresAt ? (g.expiresAt | date:'mediumDate') : '' }}</td>
                    <td>
                      @if (g.configuration) { <nz-tag>{{ 'UMS.USERS.EFF_CONFIGURATION' | translate }}</nz-tag> }
                      @else if (g.effective) { <nz-tag nzColor="green">{{ 'UMS.ROLES.IS_ACTIVE' | translate }}</nz-tag> }
                      @else { <nz-tag nzColor="red">{{ 'UMS.USERS.EFF_LAPSED' | translate }}</nz-tag> }
                    </td>
                  </tr>
                }
              </tbody>
            </nz-table>
          </nz-tab>

          <nz-tab [nzTitle]="'UMS.USERS.EFF_SOD' | translate">
            @if (report.separationOfDutiesViolations.length) {
              @for (v of report.separationOfDutiesViolations; track v.codeA + v.codeB) {
                <nz-alert nzType="error" nzShowIcon class="mb-2" [nzMessage]="v.codeA + ' + ' + v.codeB" [nzDescription]="v.reason || ''"></nz-alert>
              }
            } @else {
              <p class="text-500">{{ 'UMS.USERS.EFF_SOD_NONE' | translate }}</p>
            }
            @if (sod) {
              <h4 class="mt-3">{{ 'UMS.USERS.EFF_SOD_PAIRS' | translate }}</h4>
              @for (p of sod.pairs; track p.id) { <div class="text-sm mb-1"><code>{{ p.codeA }}</code> / <code>{{ p.codeB }}</code> <span class="text-500">{{ p.reason || '' }}</span></div> }
              <h4 class="mt-3">{{ 'UMS.USERS.EFF_SOD_VIOLATIONS' | translate }}</h4>
              @if (!sodViolationEntries.length) { <p class="text-500">{{ 'UMS.USERS.EFF_NONE' | translate }}</p> }
              @for (e of sodViolationEntries; track e.user) {
                <div class="text-sm mb-1"><strong>{{ e.user }}</strong>: @for (v of e.violations; track v.codeA + v.codeB) { <nz-tag nzColor="red">{{ v.codeA }} + {{ v.codeB }}</nz-tag> }</div>
              }
            }
          </nz-tab>

          <nz-tab [nzTitle]="('UMS.USERS.EFF_EXCEPTIONS' | translate) + ' (' + exceptions.length + ')'">
            <nz-table #excTable [nzData]="exceptions" nzSize="small" [nzPageSize]="25">
              <thead>
                <tr>
                  <th>{{ 'UMS.USERS.USERNAME' | translate }}</th>
                  <th>{{ 'UMS.USERS.PERMISSIONS' | translate }}</th>
                  <th>{{ 'UMS.USERS.EFF_REASON' | translate }}</th>
                  <th>{{ 'UMS.USERS.EFF_EXPIRES' | translate }}</th>
                </tr>
              </thead>
              <tbody>
                @for (e of excTable.data; track e.userId + e.code) {
                  <tr>
                    <td>{{ e.userName }}</td>
                    <td><code>{{ e.code }}</code> @if (e.configuration) { <nz-tag>{{ 'UMS.USERS.EFF_CONFIGURATION' | translate }}</nz-tag> }</td>
                    <td>{{ e.reason || '' }}</td>
                    <td>{{ e.expiresAt ? (e.expiresAt | date:'mediumDate') : '' }} @if (e.expired) { <nz-tag nzColor="red">{{ 'UMS.USERS.EFF_LAPSED' | translate }}</nz-tag> }</td>
                  </tr>
                }
              </tbody>
            </nz-table>
          </nz-tab>

          @if (diff) {
            <nz-tab [nzTitle]="'UMS.USERS.EFF_DIFF' | translate">
              <div class="grid">
                <div class="col-12 md:col-4">
                  <h4>{{ onlyLabel('UMS.USERS.EFF_ONLY_A', diff.userNameA) }} ({{ diff.onlyInA.length }})</h4>
                  @for (c of diff.onlyInA; track c) { <nz-tag nzColor="blue">{{ c }}</nz-tag> }
                </div>
                <div class="col-12 md:col-4">
                  <h4>{{ onlyLabel('UMS.USERS.EFF_ONLY_B', diff.userNameB) }} ({{ diff.onlyInB.length }})</h4>
                  @for (c of diff.onlyInB; track c) { <nz-tag nzColor="orange">{{ c }}</nz-tag> }
                </div>
                <div class="col-12 md:col-4">
                  <h4>{{ 'UMS.USERS.EFF_COMMON' | translate }} ({{ diff.common.length }})</h4>
                  <span class="text-500 text-sm">{{ diff.common.join(', ') }}</span>
                </div>
              </div>
            </nz-tab>
          }
        </nz-tabs>
      }
      </nz-spin>
    </div>
    `
})
export class EffectivePermissionsComponent implements OnInit {

    private readonly route = inject(ActivatedRoute);
    private readonly router = inject(Router);
    private readonly userService = inject(UmsUserService);
    private readonly authService = inject(UmsAuthService);
    private readonly translation = inject(TranslationService);
    private readonly toast = inject(ToastService);
    /**
     * Live QA L2 (#67): the reads were never unsubscribed, so leaving the page within
     * about a second let the answer arrive afterwards and its query-string update
     * navigate back here. Every read now ends with the page.
     */
    private readonly destroyRef = inject(DestroyRef);
    private readonly codeLists = inject(CodeListService);

    users: UmsUserSummary[] = [];
    /** 0 = the signed-in account (served unscoped by /me). */
    selectedUserId = 0;
    compareUserId: number | null = null;
    report: EffectivePermissionReport | null = null;
    diff: EffectivePermissionDiff | null = null;
    exceptions: DirectGrantException[] = [];
    sod: SeparationOfDutiesReport | null = null;
    filter = '';
    loading = false;

    ngOnInit(): void {
        const fromQuery = Number(this.route.snapshot.queryParamMap.get('userId'));
        if (fromQuery > 0) { this.selectedUserId = fromQuery; }
        this.userService.getAllUsers(0, 500, { sortBy: 'userName' })
            .pipe(catchError(() => of({ content: [] } as any)), takeUntilDestroyed(this.destroyRef))
            .subscribe(page => { this.users = page?.content ?? []; });
        this.load();
    }

    load(): void {
        this.loading = true;
        const report$ = this.selectedUserId > 0
            ? this.userService.getEffectivePermissions(this.selectedUserId)
            : this.userService.getMyEffectivePermissions();
        forkJoin({
            report: report$,
            exceptions: this.userService.getDirectGrantExceptions().pipe(catchError(() => of([] as DirectGrantException[]))),
            sod: this.userService.getSeparationOfDuties().pipe(catchError(() => of(null)))
        }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
            next: ({ report, exceptions, sod }) => {
                this.report = report;
                this.exceptions = exceptions;
                this.sod = sod;
                this.loading = false;
                this.router.navigate([], { relativeTo: this.route, queryParams: { userId: this.selectedUserId || null }, replaceUrl: true });
                this.loadDiff();
            },
            error: () => {
                this.loading = false;
                this.report = null;
                this.toast.error(this.translation.translate('UMS.USERS.EFF_LOAD_FAILED'));
            }
        });
    }

    loadDiff(): void {
        const a = this.selectedUserId > 0 ? this.selectedUserId : (this.authService.currentUserValue?.id ?? 0);
        if (!a || !this.compareUserId || this.compareUserId === a) { this.diff = null; return; }
        this.userService.diffEffectivePermissions(a, this.compareUserId)
            .pipe(catchError(() => of(null)), takeUntilDestroyed(this.destroyRef))
            .subscribe(d => { this.diff = d; });
    }

    get filteredPermissions() {
        const f = this.filter.trim().toUpperCase();
        const all = this.report?.permissions ?? [];
        if (!f) { return all; }
        // Searches whichever text is on screen as well as the code, so typing an
        // Arabic word in the Arabic UI finds the row the reader is looking at.
        return all.filter(p => p.code.includes(f)
            || (p.description || '').toUpperCase().includes(f)
            || (p.descriptionAr || '').toUpperCase().includes(f));
    }

    /** True while the UI is Arabic; the server sends both languages and the browser chooses. */
    private get isArabic(): boolean {
        return this.translation.getCurrentLanguage() === 'ar';
    }

    /**
     * A name the database holds in both languages — a role, a group.
     *
     * <p>Falls back to the other language rather than showing nothing: a role
     * added without an Arabic name should read as its English name, not as a
     * blank tag.
     */
    localName(name: string, nameAr: string | null | undefined): string {
        return (this.isArabic ? (nameAr || name) : name) || '';
    }

    /**
     * An attaché's name.
     *
     * <p>The server's `name` is the Arabic one — `CountryNameService.nameMap()`
     * defaults to Arabic — so before `nameEn` was sent alongside it the English
     * screen tagged every attaché in Arabic.
     */
    countryName(c: { name: string; nameEn: string | null }): string {
        return (this.isArabic ? c.name : (c.nameEn || c.name)) || '';
    }

    /**
     * The staff position, named rather than coded.
     *
     * <p>`positionCode` is a `MOHECORE.LU_POSITION` code (V093) and the row holds
     * both languages. Resolved through the same code-list service the user
     * wizard's position picker uses, so the two screens cannot drift apart.
     */
    positionName(code: string | null | undefined): string {
        if (!code) { return ''; }
        return this.codeLists.label('position', code, this.translation.getCurrentLanguage()) || code;
    }

    /**
     * The label for one permission row.
     *
     * <p>English is the catalogue's sentence, Arabic the database's name for the
     * code — different things, but each is the best the system has in that
     * language, and an Arabic reader is better served by an Arabic name than by
     * an English sentence.
     */
    permissionText(p: { description: string | null; descriptionAr: string | null }): string {
        return (this.isArabic ? (p.descriptionAr || p.description) : (p.description || p.descriptionAr)) || '';
    }

    /**
     * Where a permission came from, as words rather than as the wire format.
     *
     * <p>The server sends provenance as `ROLE:<name>`, `GROUP:<g>/ROLE:<r>`,
     * `DIRECT`, `DIRECT (until <date>)` or `SUPER_ADMIN`. Rendered raw, the
     * labels are English in both languages and the punctuation is an internal
     * detail; only the names inside are data. This translates the tokens and
     * leaves the names alone.
     */
    sourceLabel(source: string): string {
        if (!source) { return ''; }
        const t = (key: string) => this.translation.translate(key);

        if (source === 'SUPER_ADMIN') { return t('UMS.USERS.TIER_SUPER_ADMIN'); }

        if (source.startsWith('DIRECT')) {
            const until = source.match(/\(until (.+)\)/);
            return until ? `${t('UMS.USERS.EFF_DIRECT')} (${t('UMS.USERS.EFF_UNTIL')} ${until[1]})`
                         : t('UMS.USERS.EFF_DIRECT');
        }

        const viaGroupRole = source.match(/^GROUP:(.+)\/ROLE:(.+)$/);
        if (viaGroupRole) {
            return `${t('UMS.USERS.EFF_GROUP')}: ${viaGroupRole[1]} · ${t('UMS.USERS.EFF_ROLE')}: ${viaGroupRole[2]}`;
        }
        if (source.startsWith('GROUP:')) { return `${t('UMS.USERS.EFF_GROUP')}: ${source.slice(6)}`; }
        if (source.startsWith('ROLE:')) { return `${t('UMS.USERS.EFF_ROLE')}: ${source.slice(5)}`; }

        return source;
    }

    get sodViolationEntries(): { user: string; violations: { codeA: string; codeB: string; reason: string | null }[] }[] {
        const v = this.sod?.violations ?? {};
        return Object.keys(v).sort().map(user => ({ user, violations: v[user] }));
    }

    onlyLabel(key: string, name: string): string {
        return this.translation.translate(key).replace('{a}', name).replace('{b}', name);
    }

    tierColor(tier: string): string {
        switch (tier) {
            case 'SUPER_ADMIN': return 'gold';
            case 'MINISTRY_ADMIN': return 'purple';
            case 'MINISTRY_STAFF': return 'cyan';
            case 'COUNTRY_ADMIN': return 'blue';
            default: return 'default';
        }
    }

    sourceColor(source: string): string {
        if (source.startsWith('DIRECT')) { return 'orange'; }
        if (source === 'SUPER_ADMIN') { return 'gold'; }
        if (source.startsWith('GROUP:')) { return 'green'; }
        return 'blue';
    }
}
