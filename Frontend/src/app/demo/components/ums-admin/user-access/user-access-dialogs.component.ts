import { Component, EventEmitter, OnInit, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { forkJoin } from 'rxjs';

import { NzModalModule } from 'ng-zorro-antd/modal';
import { NzTransferModule, TransferItem } from 'ng-zorro-antd/transfer';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzCollapseModule } from 'ng-zorro-antd/collapse';
import { NzCheckboxModule } from 'ng-zorro-antd/checkbox';
import { NzGridModule } from 'ng-zorro-antd/grid';

import { environment } from '../../../../../environments/environment';
import { TranslatePipe } from '../../../pipes/translate.pipe';
import { ToastService } from '../../../service/toast.service';
import { CodeListService } from '../../../service/code-list.service';
import { TranslationService } from '../../../service/translation.service';
import { UmsUserService } from '../../../service/ums-user.service';
import { UmsGroupService } from '../../../service/ums-group.service';
import { UmsRoleService } from '../../../service/ums-role.service';
import { UmsAuthService } from '../../../service/ums-auth.service';
import { UmsCountryOption, UmsUserLookupsService } from '../../../service/ums-user-lookups.service';
import { UmsGroup, UmsPermission, UmsRole, UmsUser, UmsUserCountry, UmsUserSummary } from '../../../api/ums';
import { extractApiErrorMessage } from '../../../service/api-error.util';
import { fullNameOf, initialsOf } from '../user-form/user-form.model';

type Subject = UmsUser | UmsUserSummary;

/**
 * What is granted to a staff account: groups, roles (with an optional window,
 * R-21), attachés, direct permissions (time-boxed exceptions, R-14) and the
 * delegation of roles to a colleague (R-21).
 *
 * <p>One component hosted twice — by the users list behind each row's menu and
 * by the account page beside each section — so the two cannot drift. The host
 * calls {@code openGroups(user)} and the rest; the dialogs write as they go and
 * raise {@code changed} when one closes after a write, which is the host's cue
 * to re-read whatever it shows (the ATTACHES column, the account's sections).
 *
 * <p>The lists everything is chosen from (groups, roles, attachés, permission
 * codes) are read once here. A dialog whose list could not be read does not
 * open: built from an empty list it would show every item as unassigned, and
 * the transfer writes on every move (QA B10, N12). The click retries the read.
 */
@Component({
    selector: 'app-user-access-dialogs',
    standalone: true,
    imports: [
        CommonModule, FormsModule, TranslatePipe,
        NzModalModule, NzTransferModule, NzInputModule, NzSelectModule, NzIconModule,
        NzCollapseModule, NzCheckboxModule, NzGridModule
    ],
    template: `
    <ng-template #subjectHero>
      @if (subject) {
        <div class="dv-hero dv-hero--info">
          <div class="dv-hero__badge dv-hero__badge--text">{{ initials }}</div>
          <div class="dv-hero__body">
            <div class="dv-hero__eyebrow">{{ 'UMS.USERS.USERNAME' | translate }}</div>
            <div class="dv-hero__title">{{ subject.userName }}</div>
            @if (subjectName) { <div class="dv-hero__subtitle">{{ subjectName }}</div> }
          </div>
        </div>
      }
    </ng-template>

    <!-- Groups -->
    <nz-modal
      [(nzVisible)]="isGroupsModalVisible"
      [nzTitle]="'UMS.USERS.ASSIGN_GROUPS' | translate"
      nzWrapClassName="ums-dialog"
      (nzOnCancel)="handleGroupsModalCancel()"
      (nzOnOk)="handleGroupsModalOk()"
      [nzWidth]="760">
      <ng-container *nzModalContent>
        <ng-container *ngTemplateOutlet="subjectHero"></ng-container>
        <div class="dv-section">
          <nz-transfer
            [nzDataSource]="groupTransferItems"
            [nzTitles]="[('UMS.USERS.AVAILABLE_GROUPS' | translate), ('UMS.USERS.ASSIGNED_GROUPS' | translate)]"
            nzShowSearch
            (nzChange)="transferGroups($event)">
          </nz-transfer>
        </div>
      </ng-container>
    </nz-modal>

    <!-- Roles -->
    <nz-modal
      [(nzVisible)]="isRolesModalVisible"
      [nzTitle]="'UMS.USERS.ASSIGN_ROLES' | translate"
      nzWrapClassName="ums-dialog"
      (nzOnCancel)="handleRolesModalCancel()"
      (nzOnOk)="handleRolesModalOk()"
      [nzWidth]="760">
      <ng-container *nzModalContent>
        <ng-container *ngTemplateOutlet="subjectHero"></ng-container>
        <!-- R-21: an optional window on the roles added now. -->
        <div class="dv-section">
          <div class="dv-section__head">
            <span class="dv-section__icon dv-section__icon--orange"><span nz-icon nzType="clock-circle"></span></span>
            <div>
              <div class="dv-section__title">{{ 'UMS.USERS.ROLE_WINDOW.TITLE' | translate }}</div>
              <div class="dv-section__hint">{{ 'UMS.USERS.ROLE_WINDOW.HINT' | translate }}</div>
            </div>
          </div>
          <div class="dv-fields dv-fields--cols-3">
            <div class="dv-field">
              <label class="dv-label" for="uaRoleFrom">{{ 'UMS.USERS.ROLE_WINDOW.FROM' | translate }}</label>
              <input id="uaRoleFrom" nz-input type="datetime-local" [(ngModel)]="roleWindow.validFrom">
            </div>
            <div class="dv-field">
              <label class="dv-label" for="uaRoleTo">{{ 'UMS.USERS.ROLE_WINDOW.TO' | translate }}</label>
              <input id="uaRoleTo" nz-input type="datetime-local" [(ngModel)]="roleWindow.validTo">
            </div>
            <div class="dv-field">
              <label class="dv-label" for="uaRoleReason">{{ 'UMS.USERS.ROLE_WINDOW.REASON' | translate }}</label>
              <input id="uaRoleReason" nz-input type="text" [(ngModel)]="roleWindow.reason" maxlength="500">
            </div>
          </div>
        </div>
        <div class="dv-section">
          <nz-transfer
            [nzDataSource]="roleTransferItems"
            [nzTitles]="[('UMS.USERS.AVAILABLE_ROLES' | translate), ('UMS.USERS.ASSIGNED_ROLES' | translate)]"
            nzShowSearch
            (nzChange)="transferRoles($event)">
          </nz-transfer>
        </div>
      </ng-container>
    </nz-modal>

    <!-- Attachés -->
    <nz-modal
      [(nzVisible)]="isCountriesModalVisible"
      [nzTitle]="'UMS.USERS.ASSIGN_COUNTRIES' | translate"
      nzWrapClassName="ums-dialog"
      (nzOnCancel)="handleCountriesModalCancel()"
      (nzOnOk)="handleCountriesModalOk()"
      [nzWidth]="760">
      <ng-container *nzModalContent>
        <ng-container *ngTemplateOutlet="subjectHero"></ng-container>
        <div class="dv-notice dv-notice--info">
          <i class="pi pi-info-circle"></i>
          <span>{{ 'UMS.USERS.FORM.ATTACHES_HINT' | translate }}</span>
        </div>
        <div class="dv-section">
          <nz-transfer
            [nzDataSource]="countryTransferItems"
            [nzTitles]="[('UMS.USERS.AVAILABLE_COUNTRIES' | translate), ('UMS.USERS.ASSIGNED_COUNTRIES' | translate)]"
            nzShowSearch
            (nzChange)="transferCountries($event)">
          </nz-transfer>
        </div>
      </ng-container>
    </nz-modal>

    <!-- Direct permissions -->
    <nz-modal
      [(nzVisible)]="isPermissionsModalVisible"
      [nzTitle]="'UMS.USERS.ASSIGN_PERMISSIONS' | translate"
      nzWrapClassName="ums-dialog"
      (nzOnCancel)="handlePermissionsModalCancel()"
      (nzOnOk)="handlePermissionsModalOk()"
      [nzWidth]="900"
      [nzOkLoading]="saving">
      <ng-container *nzModalContent>
        <ng-container *ngTemplateOutlet="subjectHero"></ng-container>
        <div class="dv-section">
          <div class="dv-section__head">
            <span class="dv-section__icon dv-section__icon--orange"><span nz-icon nzType="clock-circle"></span></span>
            <div>
              <div class="dv-section__title">{{ 'UMS.USERS.GRANT_EXCEPTION_TITLE' | translate }}</div>
              <div class="dv-section__hint">{{ 'UMS.USERS.GRANT_EXCEPTION_HINT' | translate }}</div>
            </div>
          </div>
          <!-- Review M8: labelled and marked required while a new grant is ticked; the
               save used to fail with a toast that did not say which box was missing. -->
          <div class="dv-fields">
            <div class="dv-field">
              <label class="dv-label" for="uaGrantReason">
                {{ 'UMS.USERS.GRANT_REASON' | translate }}
                @if (grantNeedsException) { <span class="dv-required">*</span> }
              </label>
              <input id="uaGrantReason" nz-input [(ngModel)]="grantReason"
                [nzStatus]="grantSubmitted && grantNeedsException && !grantReason.trim() ? 'error' : ''"
                [placeholder]="'UMS.USERS.GRANT_REASON' | translate" maxlength="500" />
              @if (grantSubmitted && grantNeedsException && !grantReason.trim()) {
                <span class="dv-error"><i class="pi pi-exclamation-circle"></i>{{ 'COMMON.REQUIRED_FIELD' | translate }}</span>
              }
            </div>
            <div class="dv-field">
              <label class="dv-label" for="uaGrantExpires">
                {{ 'UMS.USERS.GRANT_EXPIRES' | translate }}
                @if (grantNeedsException) { <span class="dv-required">*</span> }
              </label>
              <input id="uaGrantExpires" nz-input type="date" [(ngModel)]="grantExpiresAt"
                [nzStatus]="grantSubmitted && grantNeedsException && !grantExpiresAt ? 'error' : ''"
                [min]="grantMinDate" [max]="grantMaxDate" />
              @if (grantSubmitted && grantNeedsException && !grantExpiresAt) {
                <span class="dv-error"><i class="pi pi-exclamation-circle"></i>{{ 'COMMON.REQUIRED_FIELD' | translate }}</span>
              }
            </div>
          </div>
        </div>
        <div class="dv-section">
          <nz-collapse>
            @for (pageGroup of permissionsByPage; track pageGroup.pageName) {
              <nz-collapse-panel [nzHeader]="pageGroup.pageName" [nzActive]="true">
                @for (perm of pageGroup.permissions; track perm.id) {
                  <div class="py-1">
                    <label nz-checkbox [(ngModel)]="perm.checked">
                      <strong>{{ perm.actionType }}</strong> - {{ perm.permissionName }}
                      <span class="text-500 text-xs">({{ perm.permissionCode }})</span>
                    </label>
                  </div>
                }
              </nz-collapse-panel>
            }
          </nz-collapse>
        </div>
      </ng-container>
    </nz-modal>

    <!-- R-21: delegation -->
    <nz-modal
      [(nzVisible)]="delegateVisible"
      [nzTitle]="'UMS.USERS.DELEGATE.TITLE' | translate"
      nzWrapClassName="ums-dialog"
      [nzOkLoading]="delegating"
      [nzOkDisabled]="!delegateTarget || !delegateTo"
      (nzOnCancel)="delegateVisible = false"
      (nzOnOk)="submitDelegate()">
      <ng-container *nzModalContent>
        <ng-container *ngTemplateOutlet="subjectHero"></ng-container>
        <div class="dv-notice dv-notice--info">
          <i class="pi pi-info-circle"></i>
          <span>{{ 'UMS.USERS.DELEGATE.HINT' | translate }}</span>
        </div>
        <div class="dv-section">
          <div class="dv-fields">
            <div class="dv-field dv-field--full">
              <label class="dv-label" for="uaDelegateTo">{{ 'UMS.USERS.DELEGATE.TO_USER' | translate }} <span class="dv-required">*</span></label>
              <nz-select nzId="uaDelegateTo" [(ngModel)]="delegateTarget" nzShowSearch nzServerSearch
                (nzOnSearch)="searchDelegateCandidates($event)"
                [nzPlaceHolder]="'UMS.USERS.DELEGATE.SEARCH' | translate">
                @for (u of delegateCandidates; track u.id) {
                  <nz-option [nzValue]="u.id" [nzLabel]="u.userName + (u.fullName ? ' - ' + u.fullName : '')"></nz-option>
                }
              </nz-select>
            </div>
            <div class="dv-field">
              <label class="dv-label" for="uaDelegateFrom">{{ 'UMS.USERS.ROLE_WINDOW.FROM' | translate }}</label>
              <input id="uaDelegateFrom" nz-input type="datetime-local" [(ngModel)]="delegateFrom">
            </div>
            <div class="dv-field">
              <label class="dv-label" for="uaDelegateUntil">{{ 'UMS.USERS.ROLE_WINDOW.TO' | translate }} <span class="dv-required">*</span></label>
              <input id="uaDelegateUntil" nz-input type="datetime-local" [(ngModel)]="delegateTo">
            </div>
            <div class="dv-field dv-field--full">
              <label class="dv-label" for="uaDelegateReason">{{ 'UMS.USERS.ROLE_WINDOW.REASON' | translate }}</label>
              <input id="uaDelegateReason" nz-input type="text" [(ngModel)]="delegateReason" maxlength="500">
            </div>
          </div>
        </div>
      </ng-container>
    </nz-modal>
    `
})
export class UserAccessDialogsComponent implements OnInit {

    /** A dialog closed after writing, or a save went through: the host re-reads what it shows. */
    @Output() changed = new EventEmitter<void>();

    /** The account the open dialog is about. */
    subject: Subject | null = null;

    groups: UmsGroup[] = [];
    roles: UmsRole[] = [];
    countries: UmsCountryOption[] = [];
    allPermissions: UmsPermission[] = [];
    /** The lists to choose from could not be read; the dialogs stay shut (QA B10, N12). */
    groupsLoadFailed = false;
    rolesLoadFailed = false;

    isGroupsModalVisible = false;
    isRolesModalVisible = false;
    isCountriesModalVisible = false;
    isPermissionsModalVisible = false;
    delegateVisible = false;
    saving = false;

    groupTransferItems: TransferItem[] = [];
    roleTransferItems: TransferItem[] = [];
    countryTransferItems: TransferItem[] = [];

    permissionsByPage: { pageName: string; permissions: (UmsPermission & { checked: boolean })[] }[] = [];
    private initialUserPermissionIds: number[] = [];
    /** R-14: a direct grant is an exception; the server refuses one without these. */
    grantReason = '';
    grantExpiresAt = '';
    /** Set once Save is pressed in the permissions dialog, so the required boxes are marked only then. */
    grantSubmitted = false;
    /** R-14: the server refuses an expiry more than 90 days ahead. */
    private static readonly GRANT_MAX_DAYS = 90;

    // R-21: window on the roles added in the roles dialog, and the delegation dialog.
    roleWindow = { validFrom: '', validTo: '', reason: '' };
    delegateCandidates: UmsUserSummary[] = [];
    delegateTarget: number | null = null;
    delegateFrom = '';
    delegateTo = '';
    delegateReason = '';
    delegating = false;

    private readonly codeLists = inject(CodeListService);
    private readonly lookups = inject(UmsUserLookupsService);
    private readonly authService = inject(UmsAuthService);

    constructor(
        private userService: UmsUserService,
        private groupService: UmsGroupService,
        private roleService: UmsRoleService,
        private http: HttpClient,
        private message: ToastService,
        private translationService: TranslationService
    ) { }

    get initials(): string {
        return initialsOf(this.subject);
    }

    get subjectName(): string {
        return fullNameOf(this.subject);
    }

    get grantMinDate(): string {
        return new Date(Date.now() + 86_400_000).toISOString().slice(0, 10);
    }

    get grantMaxDate(): string {
        return new Date(Date.now() + UserAccessDialogsComponent.GRANT_MAX_DAYS * 86_400_000).toISOString().slice(0, 10);
    }

    ngOnInit(): void {
        this.loadGroups();
        this.loadRoles();
        this.loadCountries();
        this.loadAllPermissions();
    }

    private t(key: string): string {
        return this.translationService.translate(key);
    }

    // ── the lists everything is chosen from ─────────────────────────────────

    loadGroups(): void {
        this.groupService.getAllActiveGroups().subscribe({
            next: groups => { this.groups = Array.isArray(groups) ? groups : []; this.groupsLoadFailed = false; },
            error: () => { this.groups = []; this.groupsLoadFailed = true; }
        });
    }

    loadRoles(): void {
        this.roleService.getAllActiveRoles().subscribe({
            next: roles => { this.roles = Array.isArray(roles) ? roles : []; this.rolesLoadFailed = false; },
            error: () => { this.roles = []; this.rolesLoadFailed = true; }
        });
    }

    loadCountries(): void {
        this.lookups.countries().subscribe({
            next: countries => this.countries = countries,
            error: () => this.countries = []
        });
    }

    loadAllPermissions(): void {
        this.http.get<UmsPermission[]>(`${environment.apiUrl}/mohe/api/v1/ums/permissions/all`).subscribe({
            next: permissions => this.allPermissions = Array.isArray(permissions) ? permissions : [],
            error: err => console.error('Failed to load permissions', err)
        });
    }

    /**
     * N-02.b: a tier role the signed-in account may not grant is not offered —
     * unless the account already holds it, so what it has is still shown.
     */
    private canOfferRole(role: UmsRole): boolean {
        switch (role.roleName) {
            case 'SUPER_ADMIN': return this.authService.mayGrantTier('SUPER_ADMIN');
            case 'MINISTRY_ADMIN': return this.authService.mayGrantTier('MINISTRY_ADMIN');
            case 'MINISTRY_STAFF': return this.authService.mayGrantTier('MINISTRY_STAFF');
            case 'COUNTRY_ADMIN': return this.authService.mayGrantTier('COUNTRY_ADMIN');
            default: return true;
        }
    }

    // ── groups ──────────────────────────────────────────────────────────────

    openGroups(user: Subject): void {
        // Retry, so a passing failure recovers on the next click.
        if (this.groupsLoadFailed) {
            this.message.error(this.t('UMS.USERS.GROUPS_LOAD_FAILED'));
            this.loadGroups();
            return;
        }
        this.subject = user;
        this.userService.getUserGroups(user.id).subscribe({
            next: userGroups => {
                const held = (userGroups ?? []).map(g => g.id);
                this.groupTransferItems = this.groups.map(g => ({
                    key: g.id.toString(),
                    title: g.groupName,
                    description: g.description || '',
                    direction: held.includes(g.id) ? 'right' : 'left'
                } as TransferItem));
                this.isGroupsModalVisible = true;
            },
            // Without this the menu item did nothing at all when the fetch failed.
            error: () => this.message.error(this.t('UMS.USERS.GROUPS_LOAD_FAILED'))
        });
    }

    transferGroups(event: any): void {
        if (!this.subject) { return; }
        const { to, list } = event;
        (list as TransferItem[]).forEach(item => {
            const groupId = parseInt(item['key'], 10);
            if (to === 'right') {
                this.userService.assignUserToGroup(this.subject!.id, groupId).subscribe({
                    next: (res: any) => res?.status === 'PENDING_APPROVAL'
                        ? this.message.info(this.t('UMS.USERS.PRIVILEGE_CHANGE_PENDING'))
                        : this.message.success(this.t('UMS.USERS.GROUP_ASSIGNED')),
                    error: err => this.message.error(extractApiErrorMessage(err) || this.t('UMS.USERS.GROUP_ASSIGN_FAILED'))
                });
            } else {
                this.userService.removeUserFromGroup(this.subject!.id, groupId).subscribe({
                    next: () => this.message.success(this.t('UMS.USERS.GROUP_REMOVED')),
                    error: err => this.message.error(extractApiErrorMessage(err) || this.t('UMS.USERS.GROUP_REMOVE_FAILED'))
                });
            }
        });
    }

    // The transfer writes as it happens, so both buttons mean "close" — Cancel
    // cannot undo what has already been saved. `changed` is what keeps the host
    // honest: the list's ATTACHES column and the account page's sections are
    // rendered from their last read.
    handleGroupsModalOk(): void {
        this.isGroupsModalVisible = false;
        this.changed.emit();
    }

    handleGroupsModalCancel(): void {
        this.handleGroupsModalOk();
    }

    // ── roles ───────────────────────────────────────────────────────────────

    openRoles(user: Subject): void {
        if (this.rolesLoadFailed) {
            this.message.error(this.t('UMS.USERS.ROLES_LOAD_FAILED'));
            this.loadRoles();
            return;
        }
        this.subject = user;
        this.roleWindow = { validFrom: '', validTo: '', reason: '' };
        this.userService.getUserRoles(user.id).subscribe({
            next: userRoles => {
                const held = (userRoles ?? []).map(r => r.id);
                this.roleTransferItems = this.roles
                    .filter(r => held.includes(r.id) || this.canOfferRole(r))
                    .map(r => ({
                        key: r.id.toString(),
                        title: r.roleName,
                        description: r.description || '',
                        direction: held.includes(r.id) ? 'right' : 'left'
                    } as TransferItem));
                this.isRolesModalVisible = true;
            },
            error: () => this.message.error(this.t('UMS.USERS.ROLES_LOAD_FAILED'))
        });
    }

    transferRoles(event: any): void {
        if (!this.subject) { return; }
        const { to, list } = event;
        (list as TransferItem[]).forEach(item => {
            const roleId = parseInt(item['key'], 10);
            if (to === 'right') {
                this.userService.assignRoleToUser(this.subject!.id, roleId, this.roleWindowPayload()).subscribe({
                    next: (res: any) => {
                        // S-03: a privileged role is requested, not applied; a second administrator signs.
                        if (res?.status === 'PENDING_APPROVAL') {
                            this.message.info(this.t('UMS.USERS.PRIVILEGE_CHANGE_PENDING'));
                        }
                    },
                    error: err => {
                        // A duplicate assignment is not a failure: the role is already there.
                        // Anything else is said — the request is silent now, so this is the
                        // only message the user gets (live QA #18).
                        const reason = extractApiErrorMessage(err);
                        if (!reason?.includes('already assigned')) {
                            this.message.error(this.t('UMS.USERS.FAILED_ASSIGN_ROLE') + (reason ? ': ' + reason : ''));
                        }
                    }
                });
            } else {
                this.userService.removeRoleFromUser(this.subject!.id, roleId).subscribe({
                    error: err => this.message.error(this.t('UMS.USERS.FAILED_REMOVE_ROLE') + ': '
                        + (extractApiErrorMessage(err) || this.t('COMMON.NOT_AVAILABLE')))
                });
            }
        });
    }

    handleRolesModalOk(): void {
        this.isRolesModalVisible = false;
        this.changed.emit();
    }

    handleRolesModalCancel(): void {
        this.handleRolesModalOk();
    }

    /** The window typed in the roles dialog, as the API wants it; undefined when empty. */
    roleWindowPayload(): { validFrom?: string | null; validTo?: string | null; reason?: string | null } | undefined {
        const w = this.roleWindow;
        if (!w.validFrom && !w.validTo && !w.reason) { return undefined; }
        return {
            validFrom: w.validFrom ? this.toIsoSeconds(w.validFrom) : null,
            validTo: w.validTo ? this.toIsoSeconds(w.validTo) : null,
            reason: w.reason || null
        };
    }

    /** `datetime-local` gives `YYYY-MM-DDTHH:mm`; the API's LocalDateTime wants seconds too. */
    private toIsoSeconds(local: string): string {
        return local.length === 16 ? local + ':00' : local;
    }

    // ── attachés ────────────────────────────────────────────────────────────

    openCountries(user: Subject): void {
        this.subject = user;
        const open = (held: number[]) => {
            this.countryTransferItems = this.countries.map(c => ({
                key: c.id.toString(),
                title: c.name,
                direction: held.includes(c.id) ? 'right' : 'left'
            } as TransferItem));
            this.isCountriesModalVisible = true;
        };
        this.userService.getUserCountries(user.id).subscribe({
            next: (rows: UmsUserCountry[]) => open((rows ?? []).filter(uc => uc.isActive).map(uc => uc.countryId)),
            // No assignment yet reads as an empty list: everything offered, nothing held.
            error: () => open([])
        });
    }

    transferCountries(event: any): void {
        if (!this.subject) { return; }
        const { to, list } = event;
        (list as TransferItem[]).forEach(item => {
            const countryId = parseInt(item['key'], 10);
            if (to === 'right') {
                this.userService.assignCountryToUser(this.subject!.id, countryId).subscribe({
                    next: () => this.message.success(this.t('UMS.SHARED.COUNTRY_ASSIGNED')),
                    error: err => this.message.error(extractApiErrorMessage(err) || this.t('UMS.SHARED.COUNTRY_ASSIGN_FAILED'))
                });
            } else {
                this.userService.removeCountryFromUser(this.subject!.id, countryId).subscribe({
                    next: () => this.message.success(this.t('UMS.SHARED.COUNTRY_REMOVED')),
                    error: err => this.message.error(extractApiErrorMessage(err) || this.t('UMS.SHARED.COUNTRY_REMOVE_FAILED'))
                });
            }
        });
    }

    handleCountriesModalOk(): void {
        this.isCountriesModalVisible = false;
        this.changed.emit();
    }

    handleCountriesModalCancel(): void {
        this.handleCountriesModalOk();
    }

    // ── direct permissions ──────────────────────────────────────────────────

    openPermissions(user: Subject): void {
        this.subject = user;
        this.grantReason = '';
        this.grantExpiresAt = '';
        this.grantSubmitted = false;
        this.userService.getUserDirectPermissions(user.id).subscribe({
            next: userPermissions => {
                const held = (userPermissions ?? []).map(p => p.id);
                this.initialUserPermissionIds = [...held];

                // Grouped by resource type and the code's first segment.
                const grouped: { [key: string]: (UmsPermission & { checked: boolean })[] } = {};
                this.allPermissions.forEach(perm => {
                    const prefix = (perm.permissionCode ?? '').split('.')[0] || 'GENERAL';
                    const key = `${perm.resourceType} - ${prefix}`;
                    (grouped[key] ??= []).push({ ...perm, checked: held.includes(perm.id) });
                });

                // Within a group, in the permission-action-type list's own order.
                const actionOrder = this.codeLists.rows('permission-action-type').map(r => r.value);
                Object.values(grouped).forEach(list =>
                    list.sort((a, b) => actionOrder.indexOf(a.actionType) - actionOrder.indexOf(b.actionType)));

                this.permissionsByPage = Object.keys(grouped).sort().map(pageName => ({ pageName, permissions: grouped[pageName] }));
                this.isPermissionsModalVisible = true;
            },
            error: err => {
                this.message.error(this.t('UMS.USERS.FAILED_LOAD_PERMISSIONS'));
                console.error(err);
            }
        });
    }

    /**
     * R-14: a newly ticked permission that is not a dashboard section is an
     * exception, and the server refuses it without a reason and an expiry.
     */
    get grantNeedsException(): boolean {
        return this.permissionsByPage.some(pg => pg.permissions.some(p =>
            p.checked && !this.isPermissionGranted(p.id) && !/^DASHBOARD_.*\.VIEW$/.test(p.permissionCode ?? '')));
    }

    handlePermissionsModalCancel(): void {
        this.isPermissionsModalVisible = false;
    }

    handlePermissionsModalOk(): void {
        if (!this.subject) { return; }

        const toGrant: number[] = [];
        const toRevoke: number[] = [];
        this.permissionsByPage.forEach(pg => pg.permissions.forEach(perm => {
            if (perm.checked && !this.isPermissionGranted(perm.id)) {
                toGrant.push(perm.id);
            } else if (!perm.checked && this.isPermissionGranted(perm.id)) {
                toRevoke.push(perm.id);
            }
        }));

        this.grantSubmitted = true;
        if (this.grantNeedsException && (!this.grantReason.trim() || !this.grantExpiresAt)) {
            this.message.warning(this.t('UMS.USERS.GRANT_EXCEPTION_REQUIRED'));
            return;
        }

        const writes = [
            toGrant.length ? this.userService.grantPermissionsToUser(this.subject.id, toGrant,
                this.grantReason.trim() || undefined, this.grantExpiresAt || undefined) : null,
            toRevoke.length ? this.userService.revokePermissionsFromUser(this.subject.id, toRevoke) : null
        ].filter(Boolean) as any[];

        if (!writes.length) {
            this.isPermissionsModalVisible = false;
            return;
        }

        this.saving = true;
        forkJoin(writes).subscribe({
            next: (results: any[]) => {
                if (results.some(r => r?.status === 'PENDING_APPROVAL')) {
                    this.message.info(this.t('UMS.USERS.PRIVILEGE_CHANGE_PENDING'));
                } else {
                    this.message.success(this.t('UMS.SHARED.PERMISSIONS_UPDATED'));
                }
                this.initialUserPermissionIds = this.permissionsByPage
                    .flatMap(pg => pg.permissions.filter(p => p.checked).map(p => p.id));
                this.saving = false;
                this.isPermissionsModalVisible = false;
                this.changed.emit();
            },
            error: err => {
                this.message.error(extractApiErrorMessage(err) || this.t('UMS.SHARED.PERMISSIONS_UPDATE_FAILED'));
                this.saving = false;
            }
        });
    }

    private isPermissionGranted(permissionId: number): boolean {
        return this.initialUserPermissionIds.includes(permissionId);
    }

    // ── R-21: delegation ────────────────────────────────────────────────────

    openDelegate(user: Subject): void {
        this.subject = user;
        this.delegateTarget = null;
        this.delegateFrom = '';
        this.delegateTo = '';
        this.delegateReason = '';
        this.delegateCandidates = [];
        this.searchDelegateCandidates('');
        this.delegateVisible = true;
    }

    /** The colleagues a delegation can go to: the accounts the caller may see, by name, without the source. */
    searchDelegateCandidates(keyword: string): void {
        this.userService.getAllUsers(0, 20, { keyword: keyword?.trim() || undefined, status: 'ACTIVE', sortBy: 'userName', sortDir: 'asc' })
            .subscribe({
                next: page => {
                    const rows = Array.isArray(page?.content) ? page.content : [];
                    this.delegateCandidates = rows.filter(u => u.id !== this.subject?.id);
                },
                error: () => this.delegateCandidates = []
            });
    }

    submitDelegate(): void {
        if (!this.subject || !this.delegateTarget || !this.delegateTo) { return; }
        this.delegating = true;
        this.userService.delegateRoles(this.subject.id, {
            toUserId: this.delegateTarget,
            validFrom: this.delegateFrom ? this.toIsoSeconds(this.delegateFrom) : null,
            validTo: this.toIsoSeconds(this.delegateTo),
            reason: this.delegateReason || null
        }).subscribe({
            next: result => {
                this.delegating = false;
                this.delegateVisible = false;
                const applied = result.applied.length;
                const pending = result.pendingRequestIds.length;
                if (pending > 0) {
                    this.message.info(`${this.t('UMS.USERS.DELEGATE.DONE')}: ${applied} · ${this.t('UMS.USERS.PRIVILEGE_CHANGE_PENDING')} (${pending})`);
                } else {
                    this.message.success(`${this.t('UMS.USERS.DELEGATE.DONE')}: ${applied}`);
                }
            },
            error: err => {
                this.delegating = false;
                this.message.error(extractApiErrorMessage(err) || this.t('UMS.USERS.DELEGATE.FAILED'));
            }
        });
    }
}
