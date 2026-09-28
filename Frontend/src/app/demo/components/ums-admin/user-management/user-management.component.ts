import { Component, OnInit, DestroyRef, ViewChild, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup } from '@angular/forms';
import { Router } from '@angular/router';

// NG-ZORRO Imports
import { NzTableModule } from 'ng-zorro-antd/table';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzModalModule, NzModalService } from 'ng-zorro-antd/modal';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { NzBadgeModule } from 'ng-zorro-antd/badge';
import { NzTooltipModule } from 'ng-zorro-antd/tooltip';
import { NzDropDownModule } from 'ng-zorro-antd/dropdown';

import { TranslatePipe } from '../../../pipes/translate.pipe';
import { ToastService } from '../../../service/toast.service';
import { UmsUserService } from '../../../service/ums-user.service';
import { UmsUserLookupsService, UmsCountryOption, UmsOption } from '../../../service/ums-user-lookups.service';
import { TranslationService } from '../../../service/translation.service';
import { UmsAuthService } from '../../../service/ums-auth.service';
import { HasPermissionDirective } from '../../../directives/has-permission.directive';
import { UmsUser, UmsUserSummary } from '../../../api/ums';
import { changesOnly } from '../../../service/reload-streams.util';
import { extractApiErrorMessage } from '../../../service/api-error.util';
import { UserAccessDialogsComponent } from '../user-access/user-access-dialogs.component';
import { UserMfaResetDialogComponent } from '../user-security/user-mfa-reset-dialog.component';
import { UserBasicFieldsComponent } from '../user-form/user-basic-fields.component';
import { buildStaffUserForm, markStaffUserFormTouched, requiredList, staffUserCreatePayload } from '../user-form/user-form.model';

/**
 * The staff users list: /ums-admin/users.
 *
 * <p>The list is one page of summaries at a time (W5-01); narrowing is the
 * server's job. Each row leads to the account page (view), the edit page, or
 * the delete confirmation, and behind its menu to what is granted to the
 * account — the dialogs of {@link UserAccessDialogsComponent}, shared with the
 * account page. "Basic info" opens a dialog that creates an account from the
 * minimum; "Add user" opens the full add page, where groups and roles are
 * granted at birth too.
 */
@Component({
    selector: 'app-user-management',
    imports: [
        CommonModule,
        FormsModule,
        ReactiveFormsModule,
        TranslatePipe,
        NzTableModule,
        NzDropDownModule,
        NzButtonModule,
        NzInputModule,
        NzIconModule,
        NzModalModule,
        NzSelectModule,
        NzTagModule,
        NzBadgeModule,
        NzTooltipModule,
        HasPermissionDirective,
        UserAccessDialogsComponent,
        UserMfaResetDialogComponent,
        UserBasicFieldsComponent
    ],
    template: `
    <!-- .ums-admin-page rather than .grid > .col-12. PrimeFlex gives .grid a
         margin of -0.5rem and hands the matching padding to the column, so a
         grid holding one full-width column is 1rem — 13px at this application's
         12.6px root — wider than the box it sits in, and every one of these nine
         pages overflowed the main container by exactly that. There is one column
         here; the card is it. The class is defined in
         assets/layout/styles/_ums-admin.scss. (No backticks here: this is an
         inline template.) -->
    <div class="ums-admin-page">
      <div class="card">
        <!-- Header Section. flex-1 on the icon-and-title block, not
             justify-content-between: under direction: rtl a flex row already
             runs right to left, and the global rule in styles.scss reverses every
             .flex.justify-content-between again, which put the heading back on
             the left and the buttons on the right in Arabic. See
             src/app/demo/components/page-headings.spec.ts. -->
        <div class="flex align-items-center mb-4 flex-wrap gap-3">
          <div class="flex align-items-center gap-3 flex-1">
            <i class="pi pi-users text-primary" style="font-size: 2.5rem"></i>
            <div>
              <h2 class="m-0 text-primary">{{ 'UMS.USERS.TITLE' | translate }}</h2>
              <p class="text-600 mt-1 mb-0">{{ 'UMS.USERS.SUBTITLE' | translate }}</p>
            </div>
          </div>
          <div class="flex gap-2 flex-wrap">
            <button nz-button nzType="primary" *hasPermission="'UMS_ADMIN.ADD'" (click)="openAddPage()">
              <span nz-icon nzType="user-add"></span>
              {{ 'UMS.USERS.ADD_USER' | translate }}
            </button>
            <button nz-button *hasPermission="'UMS_ADMIN.ADD'" (click)="openQuickAdd()">
              <span nz-icon nzType="plus"></span>
              {{ 'UMS.USERS.BASIC_INFO' | translate }}
            </button>
            <button nz-button (click)="loadUsers()" [nzLoading]="loading">
              <span nz-icon nzType="reload"></span>
              {{ 'COMMON.REFRESH' | translate }}
            </button>
          </div>
        </div>

        <!-- Filters. Shared layout: assets/layout/styles/_filter-bar.scss.
             Narrowing is the server's job: the table holds one page at a time. -->
        <div class="filter-bar">
          <div class="filter-bar__grid filter-bar__grid--cols-4">
            <div class="filter-bar__field filter-bar__field--wide" [class.filter-bar__field--active]="searchKeyword">
              <label for="umSearch">{{ 'COMMON.SEARCH' | translate }}</label>
              <nz-input-group [nzSuffix]="suffixIconSearch">
                <input id="umSearch" type="text" nz-input [placeholder]="'UMS.USERS.SEARCH_PLACEHOLDER' | translate" [(ngModel)]="searchKeyword" (keyup.enter)="applySearchFilters()" />
              </nz-input-group>
              <ng-template #suffixIconSearch>
                <span nz-icon nzType="search" (click)="applySearchFilters()" style="cursor: pointer;"></span>
              </ng-template>
            </div>
            <div class="filter-bar__field" [class.filter-bar__field--active]="filterStatus">
              <label for="umStatus">{{ 'UMS.SHARED.FILTER_STATUS' | translate }}</label>
              <nz-select nzId="umStatus" [(ngModel)]="filterStatus" (ngModelChange)="applySearchFilters()">
                <nz-option [nzLabel]="'UMS.SHARED.ALL' | translate" nzValue=""></nz-option>
                @for (opt of statusOptions; track opt.value) {
                  <nz-option [nzLabel]="opt.label" [nzValue]="opt.value"></nz-option>
                }
              </nz-select>
            </div>
            <div class="filter-bar__field" [class.filter-bar__field--active]="filterPrivilege">
              <label for="umPrivilege">{{ 'UMS.SHARED.FILTER_PRIVILEGE' | translate }}</label>
              <nz-select nzId="umPrivilege" [(ngModel)]="filterPrivilege" (ngModelChange)="applySearchFilters()">
                <nz-option [nzLabel]="'UMS.SHARED.FILTER_PRIV_ALL' | translate" nzValue=""></nz-option>
                @for (opt of privilegeOptions; track opt.value) {
                  <nz-option [nzLabel]="opt.label" [nzValue]="opt.value"></nz-option>
                }
              </nz-select>
            </div>
          </div>
          <div class="filter-bar__actions">
            <button nz-button nzType="primary" (click)="applySearchFilters()">
              <span nz-icon nzType="search"></span>
              {{ 'COMMON.SEARCH' | translate }}
            </button>
            <button nz-button [disabled]="!hasSearchFilters" (click)="clearSearchFilters()">
              <span nz-icon nzType="clear"></span>
              {{ 'COMMON.CLEAR' | translate }}
            </button>
            <span class="filter-bar__hint filter-bar__actions-spacer">{{ 'COMMON.TOTAL' | translate }}: {{ totalUsers }}</span>
          </div>
        </div>

      <!-- Users Table -->
      <nz-table
        #usersTable
        [nzData]="users"
        [nzLoading]="loading"
        [nzTotal]="totalUsers"
        [nzPageSize]="pageSize"
        [nzPageIndex]="pageIndex"
        [nzFrontPagination]="false"
        (nzPageIndexChange)="onPageChange($event)"
        (nzPageSizeChange)="onPageSizeChange($event)"
        nzShowPagination
        nzShowSizeChanger>
        <thead>
          <tr>
            <th nzShowSort nzColumnKey="userName" [nzSortOrder]="sortOrderFor('userName')" (nzSortOrderChange)="onSort('userName', $event)">{{ 'UMS.USERS.USERNAME' | translate }}</th>
            <th nzShowSort nzColumnKey="lastName" [nzSortOrder]="sortOrderFor('lastName')" (nzSortOrderChange)="onSort('lastName', $event)">{{ 'UMS.USERS.NAME' | translate }}</th>
            <th nzShowSort nzColumnKey="email" [nzSortOrder]="sortOrderFor('email')" (nzSortOrderChange)="onSort('email', $event)">{{ 'UMS.USERS.EMAIL' | translate }}</th>
            <th nzShowSort nzColumnKey="status" [nzSortOrder]="sortOrderFor('status')" (nzSortOrderChange)="onSort('status', $event)">{{ 'UMS.USERS.STATUS' | translate }}</th>
            <th>{{ 'UMS.USERS.ADMIN' | translate }}</th>
            <th nzWidth="150px">{{ 'UMS.USERS.ATTACHES' | translate }}</th>
            <th nzShowSort nzColumnKey="lastLoginAt" [nzSortOrder]="sortOrderFor('lastLoginAt')" (nzSortOrderChange)="onSort('lastLoginAt', $event)">{{ 'UMS.USERS.LAST_LOGIN' | translate }}</th>
            <th nzWidth="120px">{{ 'UMS.USERS.ACTIONS' | translate }}</th>
          </tr>
        </thead>
        <tbody>
          @for (user of usersTable.data; track user) {
            <tr>
              <td><a class="ums-row-link" dir="ltr" tabindex="0" (click)="openView(user)" (keydown.enter)="openView(user)">{{ user.userName }}</a></td>
              <td>{{ user.firstName }} {{ user.lastName }}</td>
              <td>{{ user.email }}</td>
              <td>
                <nz-badge
                  [nzStatus]="user.status === 'ACTIVE' ? 'success' : user.status === 'LOCKED' ? 'error' : 'default'"
                  [nzText]="lookups.statusLabel(user.status)">
                </nz-badge>
              </td>
              <td>
                <nz-tag [nzColor]="user.privilege === 'SUPER_ADMIN' ? 'gold' : user.privilege === 'ADMIN' ? 'blue' : 'default'">
                  {{ user.privilege === 'SUPER_ADMIN' ? ('UMS.USERS.SUPER_ADMIN' | translate) : user.privilege === 'ADMIN' ? ('UMS.USERS.ADMIN' | translate) : ('UMS.USERS.USER' | translate) }}
                </nz-tag>
              </td>
              <td>
                <!-- Two names and a count, not sixteen. One account covers every
                     country on the list, and the whole list in one cell took 324px
                     of a 981px table - a third of it, against 70px of username and
                     84px of email - and stood the row 148px tall. What the column
                     is for is telling an assigned account from one awaiting
                     assignment; the names in full are on the account page. -->
                @if (user.countryNames?.length) {
                  <div class="ums-attaches">
                    @for (name of user.countryNames.slice(0, 2); track name) {
                      <nz-tag>{{ name }}</nz-tag>
                    }
                    @if (user.countryNames.length > 2) {
                      <nz-tag [nz-tooltip]="user.countryNames.slice(2).join(', ')">+{{ user.countryNames.length - 2 }}</nz-tag>
                    }
                  </div>
                } @else {
                  <nz-tag nzColor="orange">{{ 'UMS.USERS.AWAITING_ATTACHE' | translate }}</nz-tag>
                }
              </td>
              <td>{{ user.lastLoginAt | date:'short' }}</td>
              <td>
                <!-- Three buttons and a menu, not seven. The seven took 242px —
                     a quarter of the table, and more than any column of data:
                     username had 75px, name 82px and email 96px to say who the
                     row was about. What stayed out here is what is done to a
                     user; what moved into the menu is what is granted to one. -->
                <button nz-button nzType="link" nzSize="small" *hasPermission="'UMS_ADMIN.VIEW'" (click)="openView(user)" [nz-tooltip]="'UMS.USERS.VIEW_DETAILS' | translate">
                  <span nz-icon nzType="eye"></span>
                </button>
                <button nz-button nzType="link" nzSize="small" *hasPermission="'UMS_ADMIN.EDIT'" [disabled]="isProtected(user)" (click)="openEdit(user)" [nz-tooltip]="tooltipFor(user, 'COMMON.EDIT')">
                  <span nz-icon nzType="edit"></span>
                </button>
                <button nz-button nzType="link" nzSize="small" nzDanger *hasPermission="'UMS_ADMIN.DELETE'" [disabled]="isProtected(user) || isSelf(user)" (click)="confirmDelete(user)" [nz-tooltip]="tooltipFor(user, 'COMMON.DELETE')">
                  <span nz-icon nzType="delete"></span>
                </button>
                <button nz-button nzType="link" nzSize="small" *hasPermission="'UMS_ADMIN.EDIT'"
                  nz-dropdown [nzDropdownMenu]="grantMenu" nzTrigger="click"
                  [disabled]="isProtected(user)" [nz-tooltip]="'UMS.USERS.MANAGE' | translate">
                  <span nz-icon nzType="ellipsis"></span>
                </button>
                <nz-dropdown-menu #grantMenu="nzDropdownMenu">
                  <ul nz-menu>
                    <li nz-menu-item (click)="access.openGroups(user)">
                      <span nz-icon nzType="team" class="mx-2"></span>{{ 'UMS.USERS.MANAGE_GROUPS' | translate }}
                    </li>
                    <li nz-menu-item (click)="access.openRoles(user)">
                      <span nz-icon nzType="usergroup-add" class="mx-2"></span>{{ 'UMS.USERS.MANAGE_ROLES' | translate }}
                    </li>
                    <li nz-menu-item (click)="access.openPermissions(user)">
                      <span nz-icon nzType="key" class="mx-2"></span>{{ 'UMS.USERS.MANAGE_PERMISSIONS' | translate }}
                    </li>
                    <li nz-menu-item (click)="access.openCountries(user)">
                      <span nz-icon nzType="global" class="mx-2"></span>{{ 'UMS.USERS.MANAGE_COUNTRIES' | translate }}
                    </li>
                    <li nz-menu-item (click)="openEffectivePermissions(user)">
                      <span nz-icon nzType="safety" class="mx-2"></span>{{ 'UMS.USERS.EFFECTIVE_PERMISSIONS' | translate }}
                    </li>
                    <li nz-menu-item (click)="access.openDelegate(user)">
                      <span nz-icon nzType="swap" class="mx-2"></span>{{ 'UMS.USERS.DELEGATE.ACTION' | translate }}
                    </li>
                    <li nz-menu-item nzDanger (click)="mfaReset.open(user)">
                      <span nz-icon nzType="safety" class="mx-2"></span>{{ 'UMS.USERS.RESET_MFA' | translate }}
                    </li>
                  </ul>
                </nz-dropdown-menu>
              </td>
            </tr>
          }
        </tbody>
      </nz-table>
      </div>
    </div>

    <!-- Basic info: an account from the minimum, without leaving the list. -->
    <nz-modal
      [(nzVisible)]="isQuickAddVisible"
      [nzTitle]="'UMS.USERS.QUICK_ADD_TITLE' | translate"
      nzWrapClassName="ums-dialog"
      [nzWidth]="860"
      [nzMaskClosable]="false"
      [nzFooter]="quickAddFooter"
      (nzOnCancel)="closeQuickAdd()">
      <ng-template #quickAddFooter>
        <div class="ums-dialog__footer">
          <span class="ums-dialog__footer-hint"><i class="pi pi-info-circle"></i> {{ 'UMS.USERS.QUICK_ADD_HINT' | translate }}</span>
          <div class="ums-dialog__footer-actions">
            <button nz-button (click)="closeQuickAdd()" [disabled]="saving">{{ 'COMMON.CANCEL' | translate }}</button>
            <button nz-button (click)="openFullForm()" [disabled]="saving">
              <span nz-icon nzType="form"></span>{{ 'UMS.USERS.QUICK_ADD_FULL' | translate }}
            </button>
            <button nz-button nzType="primary" (click)="submitQuickAdd()" [nzLoading]="saving">
              <span nz-icon nzType="user-add"></span>{{ 'UMS.USERS.FORM.CREATE_ACTION' | translate }}
            </button>
          </div>
        </div>
      </ng-template>
      <ng-container *nzModalContent>
        <div class="dv-hero dv-hero--new">
          <div class="dv-hero__badge"><span nz-icon nzType="user-add"></span></div>
          <div class="dv-hero__body">
            <div class="dv-hero__eyebrow">{{ 'UMS.USERS.ADD_USER' | translate }}</div>
            <div class="dv-hero__title">{{ 'UMS.USERS.BASIC_INFO' | translate }}</div>
            <div class="dv-hero__subtitle">{{ 'UMS.USERS.QUICK_ADD_SUBTITLE' | translate }}</div>
          </div>
        </div>
        <app-user-basic-fields
          [form]="quickForm"
          mode="create"
          idPrefix="qa"
          [positionOptions]="lookups.positionOptions()"
          [statusOptions]="statusOptions"
          [privilegeOptions]="privilegeOptions"
          [countryOptions]="countryOptions"
          [countriesRequired]="countriesRequired"
          [canGrantAdmin]="canGrantAdmin">
        </app-user-basic-fields>
      </ng-container>
    </nz-modal>

    <app-user-access-dialogs (changed)="loadUsers()"></app-user-access-dialogs>
    <!-- O-16: the second factor is not a column of the list, so nothing to re-read on done. -->
    <app-user-mfa-reset-dialog></app-user-mfa-reset-dialog>
    `,
    styles: [`
    .ums-row-link {
      cursor: pointer;
      color: var(--text-color);
      font-weight: 600;
    }
    .ums-row-link:hover {
      color: var(--primary-color);
    }
  `]
})
export class UserManagementComponent implements OnInit {

    /** Summaries (W5-01): the account and edit pages read the full account by id. */
    users: UmsUserSummary[] = [];

    loading = false;
    saving = false;
    searchKeyword = '';
    /** '' = no filter */
    filterStatus = '';
    filterPrivilege = '';
    /** The status and account-type lists, from the database, for the filters and the quick-add dialog alike. */
    statusOptions: UmsOption[] = [];
    privilegeOptions: UmsOption[] = [];
    countryOptions: UmsCountryOption[] = [];
    sortBy = 'userName';
    sortDir: 'asc' | 'desc' = 'asc';

    totalUsers = 0;
    pageIndex = 1;
    pageSize = 10;

    isQuickAddVisible = false;
    quickForm: FormGroup;

    /** The dialogs behind each row's menu: what is granted to an account. */
    @ViewChild(UserAccessDialogsComponent, { static: true }) access!: UserAccessDialogsComponent;

    /** O-16: clears the account's second factor (lost phone); it enrols again at its next sign-in. */
    @ViewChild(UserMfaResetDialogComponent, { static: true }) mfaReset!: UserMfaResetDialogComponent;

    // currentLanguage$ never completes, so the subscription is tied to the component lifetime.
    private readonly destroyRef = inject(DestroyRef);
    readonly lookups = inject(UmsUserLookupsService);

    constructor(
        private userService: UmsUserService,
        public translationService: TranslationService,
        private modal: NzModalService,
        private message: ToastService,
        private router: Router,
        private authService: UmsAuthService,
        private fb: FormBuilder
    ) {
        this.quickForm = buildStaffUserForm(this.fb, 'create');
    }

    /** N-02.b: whether this caller may make an administrator. */
    get canGrantAdmin(): boolean {
        return this.authService.mayGrantTier('COUNTRY_ADMIN');
    }

    /** N-02.a: a country administrator must scope a new account to one of its own attachés. */
    get countriesRequired(): boolean {
        const tier = this.authService.adminTier;
        if (typeof tier === 'string') {
            return tier === 'COUNTRY_ADMIN';
        }
        return !this.authService.isSuperAdmin() && this.authService.isMinistryUser !== true && this.authService.isAdmin();
    }

    /**
     * A super admin's row, seen by someone who is not one.
     *
     * <p>The backend refuses every write against such an account
     * (UmsPrivilegeGuard), so the buttons are disabled rather than left to fail.
     * The row itself stays visible: an account with no attaché is shown to any
     * admin so it can be assigned one, and sa_superadmin is exactly that case —
     * visible, and not actionable.
     */
    isProtected(user: UmsUser | UmsUserSummary): boolean {
        const superAdmin = 'privilege' in user ? user.privilege === 'SUPER_ADMIN' : !!user.isSuperAdmin;
        return superAdmin && !this.authService.isSuperAdmin();
    }

    /** Deleting your own account is refused server-side; it is the one with no way back. */
    isSelf(user: UmsUser | UmsUserSummary): boolean {
        return user.id === this.authService.currentUserValue?.id;
    }

    tooltipFor(user: UmsUser | UmsUserSummary, actionKey: string): string {
        if (this.isProtected(user)) {
            return this.translationService.translate('UMS.USERS.PROTECTED_SUPER_ADMIN');
        }
        // Only the delete button is disabled for your own row — editing yourself is
        // fine, so the self message must not leak onto the other actions.
        if (actionKey === 'COMMON.DELETE' && this.isSelf(user)) {
            return this.translationService.translate('UMS.USERS.CANNOT_DELETE_SELF');
        }
        return this.translationService.translate(actionKey);
    }

    ngOnInit(): void {
        this.loadOptions();
        this.translationService.currentLanguage$.pipe(changesOnly(), takeUntilDestroyed(this.destroyRef)).subscribe(() => this.loadOptions());
        this.loadUsers();
    }

    private loadOptions(): void {
        this.lookups.statusOptions().subscribe(opts => this.statusOptions = opts);
        this.lookups.privilegeOptions().subscribe(opts => this.privilegeOptions = opts);
    }

    private t(key: string): string {
        return this.translationService.translate(key);
    }

    // ── navigation ──────────────────────────────────────────────────────────

    openAddPage(): void {
        this.router.navigate(['/ums-admin/users/new']);
    }

    openView(user: UmsUser | UmsUserSummary): void {
        this.router.navigate(['/ums-admin/users', user.id]);
    }

    openEdit(user: UmsUser | UmsUserSummary): void {
        this.router.navigate(['/ums-admin/users', user.id, 'edit']);
    }

    /** R-13: the effective-permission screen for this account. */
    openEffectivePermissions(user: UmsUser | UmsUserSummary): void {
        this.router.navigate(['/ums-admin/effective-permissions'], { queryParams: { userId: user.id } });
    }

    // ── list ────────────────────────────────────────────────────────────────

    sortOrderFor(column: string): 'ascend' | 'descend' | null {
        return this.sortBy === column ? (this.sortDir === 'desc' ? 'descend' : 'ascend') : null;
    }

    onSort(column: string, order: string | null): void {
        this.sortBy = column;
        if (order === 'descend') {
            this.sortDir = 'desc';
        } else if (order === 'ascend') {
            this.sortDir = 'asc';
        } else {
            this.sortBy = 'userName';
            this.sortDir = 'asc';
        }
        this.pageIndex = 1;
        this.loadUsers();
    }

    /** Every filter back to its resting value, and the list from the first page. */
    clearSearchFilters(): void {
        this.searchKeyword = '';
        this.filterStatus = '';
        this.filterPrivilege = '';
        this.applySearchFilters();
    }

    get hasSearchFilters(): boolean {
        return !!this.searchKeyword?.trim() || !!this.filterStatus || !!this.filterPrivilege;
    }

    applySearchFilters(): void {
        this.pageIndex = 1;
        this.loadUsers();
    }

    loadUsers(): void {
        this.loading = true;
        const keyword = this.searchKeyword?.trim() || undefined;
        const status = this.filterStatus?.trim() || undefined;
        const privilege = this.filterPrivilege?.trim().toUpperCase() || undefined;
        this.userService.getAllUsers(this.pageIndex - 1, this.pageSize, {
            sortBy: this.sortBy,
            sortDir: this.sortDir,
            keyword,
            status,
            privilege: privilege === 'ADMIN' || privilege === 'REGULAR' ? privilege : undefined
        }).subscribe({
            next: (response) => {
                this.users = response.content;
                this.totalUsers = response.totalElements;
                this.loading = false;
            },
            error: () => {
                this.message.error(this.t('UMS.USERS.FAILED_LOAD'));
                this.loading = false;
            }
        });
    }

    onPageChange(pageIndex: number): void {
        this.pageIndex = pageIndex;
        this.loadUsers();
    }

    onPageSizeChange(pageSize: number): void {
        this.pageSize = pageSize;
        this.pageIndex = 1;
        this.loadUsers();
    }

    // ── basic info: the quick-add dialog ────────────────────────────────────

    openQuickAdd(): void {
        this.quickForm = buildStaffUserForm(this.fb, 'create');
        if (this.countriesRequired) {
            this.quickForm.get('countryIds')?.setValidators([requiredList]);
        }
        if (!this.countryOptions.length) {
            this.lookups.countries().subscribe({
                next: countries => this.countryOptions = countries,
                error: () => this.countryOptions = []
            });
        }
        this.isQuickAddVisible = true;
    }

    closeQuickAdd(): void {
        this.isQuickAddVisible = false;
    }

    /** The long way in: groups and roles are granted at birth on the full add page. */
    openFullForm(): void {
        this.isQuickAddVisible = false;
        this.router.navigate(['/ums-admin/users/new']);
    }

    submitQuickAdd(): void {
        if (this.quickForm.invalid) {
            markStaffUserFormTouched(this.quickForm);
            this.message.warning(this.t('COMMON.REQUIRED_FIELDS'));
            return;
        }
        this.saving = true;
        // `password`, not `passwordHash`: the server hashes what it is given, and
        // `passwordHash` is not a bindable property, so the value used to be
        // discarded and the account saved with no credential at all.
        this.userService.createUser(staffUserCreatePayload(this.quickForm)).subscribe({
            next: () => {
                this.message.success(this.t('UMS.USERS.USER_CREATED'));
                this.saving = false;
                this.isQuickAddVisible = false;
                this.loadUsers();
            },
            error: (err) => {
                this.message.error(extractApiErrorMessage(err) || this.t('UMS.USERS.FAILED_CREATE'));
                this.saving = false;
            }
        });
    }

    // ── row actions ─────────────────────────────────────────────────────────

    confirmDelete(user: UmsUser | UmsUserSummary): void {
        this.modal.confirm({
            nzTitle: this.t('UMS.USERS.DELETE_CONFIRM'),
            nzContent: `${this.t('UMS.USERS.USERNAME')}: ${user.userName}`,
            nzOkText: this.t('COMMON.YES'),
            nzOkType: 'primary',
            nzOkDanger: true,
            nzOnOk: () => {
                this.userService.deleteUser(user.id).subscribe({
                    next: () => {
                        this.message.success(this.t('UMS.USERS.USER_DELETED'));
                        this.loadUsers();
                    },
                    error: (err) => {
                        this.message.error(extractApiErrorMessage(err) || this.t('UMS.USERS.FAILED_DELETE'));
                    }
                });
            },
            nzCancelText: this.t('COMMON.NO')
        });
    }
}
