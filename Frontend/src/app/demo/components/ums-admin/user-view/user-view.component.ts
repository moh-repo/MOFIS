import { Component, DestroyRef, OnInit, ViewChild, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { EMPTY, Observable, forkJoin, of } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { silentErrors } from '../../../interceptors/http-context.tokens';

import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzSpinModule } from 'ng-zorro-antd/spin';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { NzBadgeModule } from 'ng-zorro-antd/badge';
import { NzTooltipModule } from 'ng-zorro-antd/tooltip';
import { NzDropDownModule } from 'ng-zorro-antd/dropdown';
import { NzModalService } from 'ng-zorro-antd/modal';

import { environment } from '../../../../../environments/environment';
import { TranslatePipe } from '../../../pipes/translate.pipe';
import { TranslationService } from '../../../service/translation.service';
import { ToastService } from '../../../service/toast.service';
import { UmsUserService } from '../../../service/ums-user.service';
import { UmsAuthService } from '../../../service/ums-auth.service';
import { UmsUserLookupsService } from '../../../service/ums-user-lookups.service';
import { HasPermissionDirective } from '../../../directives/has-permission.directive';
import { UmsGroup, UmsMfaStatus, UmsRole, UmsUser } from '../../../api/ums';
import { extractApiErrorMessage } from '../../../service/api-error.util';
import { UserAccessDialogsComponent } from '../user-access/user-access-dialogs.component';
import { UserMfaResetDialogComponent } from '../user-security/user-mfa-reset-dialog.component';
import { fullNameOf, initialsOf, privilegeColor, privilegeOf, statusBadge, statusTone } from '../user-form/user-form.model';

/**
 * The account page of a staff user: /ums-admin/users/:id.
 *
 * <p>Everything the list only hints at, in one place: who the account is, its
 * state (status, lockout ladder, second factor, password age), the whole of
 * what it reaches (every attaché, role, group and direct grant, not two and a
 * count), and the actions an administrator takes on it. What is granted is
 * changed from here through {@link UserAccessDialogsComponent}; what the
 * account <em>is</em> is changed on the edit page.
 *
 * <p>Review M8: each read stands alone. A 403 on one part (a caller who may see
 * the account but not its permission codes) leaves that part marked as unread
 * rather than shown as "none", and the page still opens.
 */
@Component({
    selector: 'app-user-view',
    standalone: true,
    imports: [
        CommonModule, TranslatePipe,
        NzButtonModule, NzIconModule, NzSpinModule, NzTagModule, NzBadgeModule, NzTooltipModule, NzDropDownModule,
        HasPermissionDirective, UserAccessDialogsComponent, UserMfaResetDialogComponent
    ],
    template: `
    <div class="ums-admin-page">
      <div class="card">
        <div class="flex align-items-center mb-4 flex-wrap gap-3">
          <div class="flex align-items-center gap-3 flex-1">
            <button nz-button nzType="text" nzShape="circle" class="ums-back" (click)="goToList()" [nz-tooltip]="'COMMON.BACK' | translate">
              <span nz-icon [nzType]="translationService.isRTL() ? 'arrow-right' : 'arrow-left'"></span>
            </button>
            <i class="pi pi-id-card text-primary" style="font-size: 2.5rem"></i>
            <div>
              <div class="ums-crumbs">
                <a tabindex="0" (click)="goToList()" (keydown.enter)="goToList()">{{ 'UMS.USERS.TITLE' | translate }}</a>
                <span class="ums-crumbs__sep">/</span>
                <span dir="ltr">{{ user?.userName || '…' }}</span>
              </div>
              <h2 class="m-0 text-primary">{{ fullName || user?.userName || ('UMS.USERS.VIEW.TITLE' | translate) }}</h2>
              <p class="text-600 mt-1 mb-0">{{ 'UMS.USERS.VIEW.SUBTITLE' | translate }}</p>
            </div>
          </div>
          <div class="flex gap-2 flex-wrap">
            <button nz-button nzType="primary" *hasPermission="'UMS_ADMIN.EDIT'" [disabled]="!user || protected" (click)="edit()"
              [nz-tooltip]="protected ? ('UMS.USERS.PROTECTED_SUPER_ADMIN' | translate) : ''">
              <span nz-icon nzType="edit"></span>{{ 'COMMON.EDIT' | translate }}
            </button>
            <button nz-button *hasPermission="'UMS_ADMIN.EDIT'" [disabled]="!user || protected"
              nz-dropdown [nzDropdownMenu]="manageMenu" nzTrigger="click">
              <span nz-icon nzType="safety"></span>{{ 'UMS.USERS.MANAGE' | translate }}<span nz-icon nzType="down"></span>
            </button>
            <nz-dropdown-menu #manageMenu="nzDropdownMenu">
              <ul nz-menu>
                <li nz-menu-item (click)="access.openCountries(user!)"><span nz-icon nzType="global" class="mx-2"></span>{{ 'UMS.USERS.MANAGE_COUNTRIES' | translate }}</li>
                <li nz-menu-item (click)="access.openRoles(user!)"><span nz-icon nzType="usergroup-add" class="mx-2"></span>{{ 'UMS.USERS.MANAGE_ROLES' | translate }}</li>
                <li nz-menu-item (click)="access.openGroups(user!)"><span nz-icon nzType="team" class="mx-2"></span>{{ 'UMS.USERS.MANAGE_GROUPS' | translate }}</li>
                <li nz-menu-item (click)="access.openPermissions(user!)"><span nz-icon nzType="key" class="mx-2"></span>{{ 'UMS.USERS.MANAGE_PERMISSIONS' | translate }}</li>
                <li nz-menu-divider></li>
                <li nz-menu-item (click)="access.openDelegate(user!)"><span nz-icon nzType="swap" class="mx-2"></span>{{ 'UMS.USERS.DELEGATE.ACTION' | translate }}</li>
              </ul>
            </nz-dropdown-menu>
            <button nz-button (click)="load()" [nzLoading]="loading">
              <span nz-icon nzType="reload"></span>{{ 'COMMON.REFRESH' | translate }}
            </button>
          </div>
        </div>

        <nz-spin [nzSpinning]="loading">
          @if (loadFailed) {
            <div class="dv-notice dv-notice--danger">
              <i class="pi pi-exclamation-triangle"></i>
              <span>{{ 'UMS.USERS.VIEW.NOT_FOUND' | translate }}</span>
              <button nz-button nzSize="small" class="ums-notice__action" (click)="load()">{{ 'COMMON.RETRY' | translate }}</button>
              <button nz-button nzSize="small" class="ums-notice__action" (click)="goToList()">{{ 'COMMON.BACK' | translate }}</button>
            </div>
          }

          @if (user) {
            <!-- Identity strip -->
            <div class="dv-hero" [ngClass]="statusTone(user.status)">
              <div class="dv-hero__badge dv-hero__badge--text">{{ initials }}</div>
              <div class="dv-hero__body">
                <div class="dv-hero__eyebrow">{{ 'UMS.USERS.VIEW.EYEBROW' | translate }}</div>
                <div class="dv-hero__title">{{ fullName || user.userName }}</div>
                <div class="dv-hero__subtitle" dir="ltr">{{ user.userName }}@if (user.email) { · {{ user.email }} }</div>
                <div class="dv-hero__meta">
                  <nz-badge [nzStatus]="statusBadge(user.status)" [nzText]="lookups.statusLabel(user.status)"></nz-badge>
                  <nz-tag [nzColor]="privilegeColor(privilege)">
                    {{ privilege === 'SUPER_ADMIN' ? ('UMS.USERS.SUPER_ADMIN' | translate) : privilege === 'ADMIN' ? ('UMS.USERS.ADMIN' | translate) : ('UMS.USERS.USER' | translate) }}
                  </nz-tag>
                  @if (user.isMinistryUser) { <span class="dv-chip dv-chip--blue"><i class="pi pi-building"></i>{{ 'UMS.USERS.VIEW.MINISTRY_USER' | translate }}</span> }
                  @if (user.positionCode) { <span class="dv-chip"><i class="pi pi-briefcase"></i>{{ lookups.positionLabel(user.positionCode) }}</span> }
                  @if (user.countryNames?.length) {
                    <span class="dv-chip dv-chip--orange"><i class="pi pi-globe"></i>{{ user.countryNames!.length }} {{ 'UMS.USERS.ATTACHES' | translate }}</span>
                  } @else {
                    <span class="dv-chip dv-chip--orange"><i class="pi pi-globe"></i>{{ 'UMS.USERS.AWAITING_ATTACHE' | translate }}</span>
                  }
                  @if (user.mustChangePassword) { <span class="dv-chip dv-chip--red"><i class="pi pi-key"></i>{{ 'UMS.USERS.VIEW.MUST_CHANGE_PASSWORD' | translate }}</span> }
                </div>
              </div>
            </div>

            <!-- The account's state at a glance -->
            <div class="dv-kpis dv-kpis--cols-4">
              <div class="dv-kpi dv-kpi--blue">
                <span class="dv-kpi__icon"><i class="pi pi-sign-in"></i></span>
                <div class="dv-kpi__body">
                  <div class="dv-kpi__label">{{ 'UMS.USERS.LAST_LOGIN' | translate }}</div>
                  <div class="dv-kpi__value">{{ user.lastLoginAt ? (user.lastLoginAt | date:'medium') : ('UMS.USERS.NEVER' | translate) }}</div>
                </div>
              </div>
              <div class="dv-kpi dv-kpi--purple">
                <span class="dv-kpi__icon"><i class="pi pi-key"></i></span>
                <div class="dv-kpi__body">
                  <div class="dv-kpi__label">{{ 'UMS.USERS.VIEW.PASSWORD_CHANGED' | translate }}</div>
                  <div class="dv-kpi__value">{{ user.passwordChangedAt ? (user.passwordChangedAt | date:'mediumDate') : ('UMS.USERS.NEVER' | translate) }}</div>
                  @if (user.mustChangePassword) { <div class="dv-kpi__sub">{{ 'UMS.USERS.VIEW.MUST_CHANGE_PASSWORD' | translate }}</div> }
                </div>
              </div>
              <div class="dv-kpi" [ngClass]="user.status === 'LOCKED' ? 'dv-kpi--red' : (user.failedLoginAttempts ? 'dv-kpi--orange' : 'dv-kpi--green')">
                <span class="dv-kpi__icon"><i class="pi" [class.pi-lock]="user.status === 'LOCKED'" [class.pi-shield]="user.status !== 'LOCKED'"></i></span>
                <div class="dv-kpi__body">
                  <div class="dv-kpi__label">{{ 'UMS.USERS.VIEW.FAILED_ATTEMPTS' | translate }}</div>
                  <div class="dv-kpi__value">{{ user.failedLoginAttempts ?? 0 }}</div>
                  @if (user.status === 'LOCKED') {
                    <div class="dv-kpi__sub">{{ user.lockedUntil ? (('UMS.USERS.VIEW.LOCKED_UNTIL' | translate) + ' ' + (user.lockedUntil | date:'short')) : ('UMS.USERS.VIEW.LOCK_MANUAL' | translate) }}</div>
                  } @else if (user.lockoutStage) {
                    <div class="dv-kpi__sub">{{ 'UMS.USERS.VIEW.LOCKOUT_STAGE' | translate }} {{ user.lockoutStage }}</div>
                  }
                </div>
              </div>
              <div class="dv-kpi" [ngClass]="mfa?.enabled ? 'dv-kpi--green' : 'dv-kpi--gray'">
                <span class="dv-kpi__icon"><i class="pi pi-mobile"></i></span>
                <div class="dv-kpi__body">
                  <div class="dv-kpi__label">{{ 'UMS.USERS.VIEW.MFA' | translate }}</div>
                  <div class="dv-kpi__value">
                    @if (failed.mfa) { {{ 'COMMON.NOT_AVAILABLE' | translate }} }
                    @else { {{ (mfa?.enabled ? 'UMS.USERS.VIEW.MFA_ENABLED' : 'UMS.USERS.VIEW.MFA_NOT_ENROLLED') | translate }} }
                  </div>
                  @if (mfa?.enabled) {
                    <div class="dv-kpi__sub">{{ 'UMS.USERS.VIEW.MFA_RECOVERY_LEFT' | translate }}: {{ mfa!.recoveryCodesLeft }}</div>
                  } @else if (mfa?.required) {
                    <div class="dv-kpi__sub">{{ 'UMS.USERS.VIEW.MFA_REQUIRED' | translate }}</div>
                  }
                </div>
              </div>
            </div>

            <div class="ums-detail-layout">
              <div class="ums-detail-layout__main ums-stack">
                <!-- Account -->
                <div class="dv-section">
                  <div class="dv-section__head">
                    <span class="dv-section__icon"><span nz-icon nzType="idcard"></span></span>
                    <div><div class="dv-section__title">{{ 'UMS.USERS.VIEW.SECTION_ACCOUNT' | translate }}</div></div>
                  </div>
                  <div class="ums-dl-columns">
                    <dl class="dv-dl">
                      <div class="dv-dl__row"><dt>{{ 'UMS.USERS.USERNAME' | translate }}</dt><dd dir="ltr">{{ user.userName }}</dd></div>
                      <div class="dv-dl__row"><dt>{{ 'UMS.USERS.FIRST_NAME' | translate }}</dt><dd>{{ user.firstName || ('COMMON.NOT_AVAILABLE' | translate) }}</dd></div>
                      <div class="dv-dl__row"><dt>{{ 'UMS.USERS.LAST_NAME' | translate }}</dt><dd>{{ user.lastName || ('COMMON.NOT_AVAILABLE' | translate) }}</dd></div>
                      <div class="dv-dl__row"><dt>{{ 'UMS.USERS.POSITION' | translate }}</dt><dd [class.dv-dl__muted]="!user.positionCode">{{ lookups.positionLabel(user.positionCode) || ('UMS.USERS.POSITION_NONE' | translate) }}</dd></div>
                    </dl>
                    <dl class="dv-dl">
                      <div class="dv-dl__row"><dt>{{ 'UMS.USERS.EMAIL' | translate }}</dt><dd dir="ltr" [class.dv-dl__muted]="!user.email">{{ user.email || ('COMMON.NOT_AVAILABLE' | translate) }}</dd></div>
                      <div class="dv-dl__row"><dt>{{ 'UMS.USERS.PHONE' | translate }}</dt><dd dir="ltr" [class.dv-dl__muted]="!user.phone">{{ user.phone || ('COMMON.NOT_AVAILABLE' | translate) }}</dd></div>
                      <div class="dv-dl__row"><dt>{{ 'UMS.USERS.STATUS' | translate }}</dt><dd><nz-badge [nzStatus]="statusBadge(user.status)" [nzText]="lookups.statusLabel(user.status)"></nz-badge></dd></div>
                      <div class="dv-dl__row"><dt>{{ 'UMS.USERS.ACCOUNT_TYPE' | translate }}</dt>
                        <dd><nz-tag [nzColor]="privilegeColor(privilege)">{{ privilege === 'SUPER_ADMIN' ? ('UMS.USERS.SUPER_ADMIN' | translate) : privilege === 'ADMIN' ? ('UMS.USERS.ADMIN' | translate) : ('UMS.USERS.USER' | translate) }}</nz-tag></dd>
                      </div>
                    </dl>
                  </div>
                </div>

                <!-- Attachés: the list shows two and a count; this is where the whole scope is named. -->
                <div class="dv-section">
                  <div class="dv-section__head">
                    <span class="dv-section__icon dv-section__icon--orange"><span nz-icon nzType="global"></span></span>
                    <div>
                      <div class="dv-section__title">{{ 'UMS.USERS.ATTACHES' | translate }} ({{ user.countryNames?.length || 0 }})</div>
                      <div class="dv-section__hint">{{ 'UMS.USERS.FORM.ATTACHES_HINT' | translate }}</div>
                    </div>
                    <div class="dv-section__aside" *hasPermission="'UMS_ADMIN.EDIT'">
                      <button nz-button nzSize="small" [disabled]="protected" (click)="access.openCountries(user)"><span nz-icon nzType="edit"></span>{{ 'UMS.USERS.VIEW.MANAGE' | translate }}</button>
                    </div>
                  </div>
                  @if (user.countryNames?.length) {
                    <div class="ums-tags">
                      @for (name of user.countryNames; track name) { <nz-tag nzColor="orange">{{ name }}</nz-tag> }
                    </div>
                  } @else {
                    <div class="dv-notice dv-notice--warn" style="margin:0"><i class="pi pi-exclamation-triangle"></i><span>{{ 'UMS.USERS.EFF_NOTE_NO_ATTACHE' | translate }}</span></div>
                  }
                </div>

                <!-- Roles -->
                <div class="dv-section">
                  <div class="dv-section__head">
                    <span class="dv-section__icon dv-section__icon--blue"><span nz-icon nzType="safety"></span></span>
                    <div>
                      <div class="dv-section__title">{{ 'UMS.USERS.ROLES' | translate }} ({{ roles.length }})</div>
                      <div class="dv-section__hint">{{ 'UMS.USER_WIZARD.ROLES_DESC' | translate }}</div>
                    </div>
                    <div class="dv-section__aside" *hasPermission="'UMS_ADMIN.EDIT'">
                      <button nz-button nzSize="small" [disabled]="protected" (click)="access.openRoles(user)"><span nz-icon nzType="edit"></span>{{ 'UMS.USERS.VIEW.MANAGE' | translate }}</button>
                    </div>
                  </div>
                  @if (roles.length) {
                    <div class="ums-tags">
                      @for (r of roles; track r.id) { <nz-tag nzColor="blue">{{ localName(r.roleName, r.roleNameAr) }}</nz-tag> }
                    </div>
                  } @else if (failed.roles) {
                    <div class="dv-notice dv-notice--warn part-not-loaded" style="margin:0"><i class="pi pi-exclamation-triangle"></i><span>{{ 'UMS.USERS.ROLES_LOAD_FAILED' | translate }}</span></div>
                  } @else {
                    <div class="dv-notice dv-notice--muted" style="margin:0"><i class="pi pi-info-circle"></i><span>{{ 'UMS.USER_WIZARD.NO_SELECTION' | translate }}</span></div>
                  }
                </div>

                <!-- Groups -->
                <div class="dv-section">
                  <div class="dv-section__head">
                    <span class="dv-section__icon dv-section__icon--green"><span nz-icon nzType="team"></span></span>
                    <div>
                      <div class="dv-section__title">{{ 'UMS.USERS.GROUPS' | translate }} ({{ groups.length }})</div>
                      <div class="dv-section__hint">{{ 'UMS.USER_WIZARD.GROUPS_DESC' | translate }}</div>
                    </div>
                    <div class="dv-section__aside" *hasPermission="'UMS_ADMIN.EDIT'">
                      <button nz-button nzSize="small" [disabled]="protected" (click)="access.openGroups(user)"><span nz-icon nzType="edit"></span>{{ 'UMS.USERS.VIEW.MANAGE' | translate }}</button>
                    </div>
                  </div>
                  @if (groups.length) {
                    <div class="ums-tags">
                      @for (g of groups; track g.id) { <nz-tag nzColor="green">{{ localName(g.groupName, g.groupNameAr) }}</nz-tag> }
                    </div>
                  } @else if (failed.groups) {
                    <div class="dv-notice dv-notice--warn part-not-loaded" style="margin:0"><i class="pi pi-exclamation-triangle"></i><span>{{ 'UMS.USERS.GROUPS_LOAD_FAILED' | translate }}</span></div>
                  } @else {
                    <div class="dv-notice dv-notice--muted" style="margin:0"><i class="pi pi-info-circle"></i><span>{{ 'UMS.USER_WIZARD.NO_SELECTION' | translate }}</span></div>
                  }
                </div>

                <!-- Permission codes the account holds -->
                <div class="dv-section">
                  <div class="dv-section__head">
                    <span class="dv-section__icon dv-section__icon--purple"><span nz-icon nzType="key"></span></span>
                    <div>
                      <div class="dv-section__title">{{ 'UMS.USERS.PERMISSIONS' | translate }} ({{ permissionCodes.length }})</div>
                      <div class="dv-section__hint">{{ 'UMS.USERS.VIEW.PERMISSIONS_HINT' | translate }}</div>
                    </div>
                    <div class="dv-section__aside" *hasPermission="'UMS_ADMIN.EDIT'">
                      <button nz-button nzSize="small" [disabled]="protected" (click)="access.openPermissions(user)"><span nz-icon nzType="edit"></span>{{ 'UMS.USERS.VIEW.MANAGE' | translate }}</button>
                    </div>
                  </div>
                  @if (permissionCodes.length) {
                    <div class="ums-tags">
                      @for (code of visiblePermissionCodes; track code) { <nz-tag>{{ code }}</nz-tag> }
                    </div>
                    @if (permissionCodes.length > permissionPreview) {
                      <button nz-button nzType="link" nzSize="small" class="ums-more" (click)="showAllPermissions = !showAllPermissions">
                        {{ showAllPermissions ? ('UMS.USERS.VIEW.SHOW_LESS' | translate) : ('UMS.USERS.VIEW.SHOW_ALL' | translate) + ' (' + permissionCodes.length + ')' }}
                      </button>
                    }
                  } @else if (failed.permissions) {
                    <div class="dv-notice dv-notice--warn part-not-loaded" style="margin:0"><i class="pi pi-exclamation-triangle"></i><span>{{ 'UMS.USERS.FAILED_LOAD_PERMISSIONS' | translate }}</span></div>
                  } @else {
                    <div class="dv-notice dv-notice--muted" style="margin:0"><i class="pi pi-info-circle"></i><span>{{ 'UMS.USER_WIZARD.NO_SELECTION' | translate }}</span></div>
                  }
                </div>
              </div>

              <aside class="ums-detail-layout__aside ums-stack">
                <!-- Security -->
                <div class="dv-section">
                  <div class="dv-section__head">
                    <span class="dv-section__icon dv-section__icon--red"><span nz-icon nzType="lock"></span></span>
                    <div>
                      <div class="dv-section__title">{{ 'UMS.USERS.VIEW.SECTION_SECURITY' | translate }}</div>
                      <div class="dv-section__hint">{{ 'UMS.USERS.VIEW.SECURITY_HINT' | translate }}</div>
                    </div>
                  </div>
                  <div class="ums-action-list">
                    <button nz-button nzBlock (click)="openEffectivePermissions()">
                      <span nz-icon nzType="safety-certificate"></span>{{ 'UMS.USERS.EFFECTIVE_PERMISSIONS' | translate }}
                    </button>
                    @if (user.status === 'LOCKED') {
                      <button nz-button nzBlock nzType="primary" *hasPermission="'UMS_ADMIN.EDIT'" [disabled]="protected" (click)="confirmUnlock()">
                        <span nz-icon nzType="unlock"></span>{{ 'UMS.USERS.VIEW.UNLOCK' | translate }}
                      </button>
                    }
                    <button nz-button nzBlock *hasPermission="'UMS_ADMIN.EDIT'" [disabled]="protected" (click)="access.openDelegate(user)">
                      <span nz-icon nzType="swap"></span>{{ 'UMS.USERS.DELEGATE.ACTION' | translate }}
                    </button>
                    <button nz-button nzBlock nzDanger *hasPermission="'UMS_ADMIN.EDIT'" [disabled]="protected" (click)="mfaReset.open(user)">
                      <span nz-icon nzType="safety"></span>{{ 'UMS.USERS.RESET_MFA' | translate }}
                    </button>
                    <button nz-button nzBlock nzDanger *hasPermission="'UMS_ADMIN.DELETE'" [disabled]="protected || isSelf"
                      (click)="confirmDelete()" [nz-tooltip]="isSelf ? ('UMS.USERS.CANNOT_DELETE_SELF' | translate) : ''">
                      <span nz-icon nzType="delete"></span>{{ 'COMMON.DELETE' | translate }}
                    </button>
                  </div>
                </div>

                <!-- Record -->
                <div class="dv-section">
                  <div class="dv-section__head">
                    <span class="dv-section__icon dv-section__icon--gray"><span nz-icon nzType="history"></span></span>
                    <div><div class="dv-section__title">{{ 'UMS.USERS.VIEW.SECTION_RECORD' | translate }}</div></div>
                  </div>
                  <dl class="dv-dl">
                    <div class="dv-dl__row"><dt>{{ 'UMS.USERS.CREATED' | translate }}</dt><dd>{{ user.createdAt ? (user.createdAt | date:'medium') : ('COMMON.NOT_AVAILABLE' | translate) }}</dd></div>
                    <div class="dv-dl__row"><dt>{{ 'UMS.USERS.VIEW.UPDATED' | translate }}</dt><dd [class.dv-dl__muted]="!user.updatedAt">{{ user.updatedAt ? (user.updatedAt | date:'medium') : ('COMMON.NOT_AVAILABLE' | translate) }}</dd></div>
                    <div class="dv-dl__row"><dt>{{ 'UMS.USERS.VIEW.LAST_FAILED' | translate }}</dt><dd [class.dv-dl__muted]="!user.lastFailedLoginAt">{{ user.lastFailedLoginAt ? (user.lastFailedLoginAt | date:'medium') : ('UMS.USERS.NEVER' | translate) }}</dd></div>
                    <div class="dv-dl__row"><dt>{{ 'COMMON.ID' | translate }}</dt><dd>#{{ user.id }}</dd></div>
                  </dl>
                </div>
              </aside>
            </div>
          }
        </nz-spin>
      </div>

      <app-user-access-dialogs (changed)="load()"></app-user-access-dialogs>
      <!-- O-16: the second-factor tile above is rendered from the last read. -->
      <app-user-mfa-reset-dialog (done)="load()"></app-user-mfa-reset-dialog>
    </div>
    `
})
export class UserViewComponent implements OnInit {

    userId: number | null = null;
    user: UmsUser | null = null;
    roles: UmsRole[] = [];
    groups: UmsGroup[] = [];
    permissionCodes: string[] = [];
    mfa: UmsMfaStatus | null = null;
    /** The parts that could not be read — shown as such, not as "none". */
    failed = { roles: false, groups: false, permissions: false, mfa: false };

    loading = false;
    loadFailed = false;
    showAllPermissions = false;
    readonly permissionPreview = 18;

    readonly statusBadge = statusBadge;
    readonly statusTone = statusTone;
    readonly privilegeColor = privilegeColor;

    @ViewChild(UserAccessDialogsComponent, { static: true }) access!: UserAccessDialogsComponent;

    /** O-16: clears the account's second factor (lost phone); it enrols again at its next sign-in. */
    @ViewChild(UserMfaResetDialogComponent, { static: true }) mfaReset!: UserMfaResetDialogComponent;

    private readonly destroyRef = inject(DestroyRef);
    private readonly authService = inject(UmsAuthService);
    private readonly http = inject(HttpClient);
    private readonly modal = inject(NzModalService);
    readonly lookups = inject(UmsUserLookupsService);

    constructor(
        private route: ActivatedRoute,
        private router: Router,
        private userService: UmsUserService,
        private message: ToastService,
        public translationService: TranslationService
    ) { }

    get initials(): string {
        return initialsOf(this.user);
    }

    get fullName(): string {
        return fullNameOf(this.user);
    }

    get privilege(): 'SUPER_ADMIN' | 'ADMIN' | 'USER' {
        return privilegeOf(this.user);
    }

    /** A super admin's account, seen by someone who is not one: the server refuses every write (UmsPrivilegeGuard). */
    get protected(): boolean {
        return !!this.user?.isSuperAdmin && !this.authService.isSuperAdmin();
    }

    /** Deleting your own account is refused server-side; it is the one with no way back. */
    get isSelf(): boolean {
        return !!this.user && this.user.id === this.authService.currentUserValue?.id;
    }

    get visiblePermissionCodes(): string[] {
        return this.showAllPermissions ? this.permissionCodes : this.permissionCodes.slice(0, this.permissionPreview);
    }

    localName(en: string, ar: string | null | undefined): string {
        return this.translationService.getCurrentLanguage() === 'ar' && ar ? ar : en;
    }

    ngOnInit(): void {
        this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
            const raw = params.get('id');
            this.userId = raw ? Number(raw) : null;
            this.load();
        });
    }

    private t(key: string): string {
        return this.translationService.translate(key);
    }

    /**
     * Review M8: each read stands alone. The account itself is the one part
     * the page cannot do without; every other part falls back to empty and is
     * marked as unread.
     */
    load(): void {
        if (!this.userId || !Number.isInteger(this.userId) || this.userId <= 0) {
            // Live QA L3 (#66): a bad id is the app's not-found page, as for students
            // and finance records, not this page under "Record not found" toasts.
            this.router.navigate(['/notfound'], { replaceUrl: true });
            return;
        }
        const id = this.userId;
        const failed = { roles: false, groups: false, permissions: false, mfa: false };
        let partial = false;
        const soft = <T>(obs: Observable<T>, fallback: T, part: keyof typeof failed) =>
            obs.pipe(catchError(() => {
                partial = true;
                failed[part] = true;
                return of(fallback);
            }));
        this.loading = true;
        this.loadFailed = false;
        // The account first, without the global "not found" toast: an id that names no
        // account goes to the not-found page (L3), and the four other reads, which would
        // each have toasted a 404, are made only for an account that exists.
        this.userService.getUserById(id, silentErrors()).pipe(
            catchError((err: HttpErrorResponse) => {
                if (err?.status === 404) {
                    this.router.navigate(['/notfound'], { replaceUrl: true });
                    return EMPTY;
                }
                return of(null);
            }),
            switchMap(user => forkJoin({
                user: of(user),
                roles: user ? soft(this.userService.getUserRoles(id), [] as UmsRole[], 'roles') : of([] as UmsRole[]),
                groups: user ? soft(this.userService.getUserGroups(id), [] as UmsGroup[], 'groups') : of([] as UmsGroup[]),
                permissionCodes: user
                    ? soft(this.http.get<string[]>(`${environment.apiUrl}/mohe/api/v1/ums/permissions/user/${id}/codes`), [] as string[], 'permissions')
                    : of([] as string[]),
                mfa: user ? soft(this.userService.getMfaStatus(id), null as UmsMfaStatus | null, 'mfa') : of(null as UmsMfaStatus | null)
            })),
            takeUntilDestroyed(this.destroyRef)
        ).subscribe(({ user, roles, groups, permissionCodes, mfa }) => {
            this.loading = false;
            if (!user || typeof user !== 'object' || !('id' in user)) {
                this.user = null;
                this.loadFailed = true;
                this.message.error(this.t('UMS.USERS.VIEW.NOT_FOUND'));
                return;
            }
            this.user = user;
            this.roles = Array.isArray(roles) ? roles : [];
            this.groups = Array.isArray(groups) ? groups : [];
            this.permissionCodes = Array.isArray(permissionCodes) ? permissionCodes : [];
            this.mfa = mfa && typeof mfa === 'object' && 'enabled' in mfa ? mfa : null;
            this.failed = failed;
            if (partial) {
                this.message.warning(this.t('UMS.USERS.DETAIL_PARTIAL'));
            }
        });
    }

    edit(): void {
        if (this.userId) {
            this.router.navigate(['/ums-admin/users', this.userId, 'edit']);
        }
    }

    goToList(): void {
        this.router.navigate(['/ums-admin/users']);
    }

    /** R-13: the effective-permission screen for this account. */
    openEffectivePermissions(): void {
        this.router.navigate(['/ums-admin/effective-permissions'], { queryParams: { userId: this.userId } });
    }

    /** V080: lifts the lockout ladder; the only way out of its last rung. */
    confirmUnlock(): void {
        if (!this.user) { return; }
        this.modal.confirm({
            nzTitle: this.t('UMS.USERS.VIEW.UNLOCK_CONFIRM'),
            nzContent: `${this.t('UMS.USERS.USERNAME')}: ${this.user.userName}`,
            nzOkText: this.t('COMMON.YES'),
            nzCancelText: this.t('COMMON.NO'),
            nzOnOk: () => {
                this.userService.unlockUser(this.user!.id).subscribe({
                    next: () => { this.message.success(this.t('UMS.USERS.VIEW.UNLOCK_DONE')); this.load(); },
                    error: err => this.message.error(extractApiErrorMessage(err) || this.t('UMS.USERS.VIEW.FAILED_UNLOCK'))
                });
            }
        });
    }

    confirmDelete(): void {
        if (!this.user) { return; }
        this.modal.confirm({
            nzTitle: this.t('UMS.USERS.DELETE_CONFIRM'),
            nzContent: `${this.t('UMS.USERS.USERNAME')}: ${this.user.userName}`,
            nzOkText: this.t('COMMON.YES'),
            nzOkType: 'primary',
            nzOkDanger: true,
            nzCancelText: this.t('COMMON.NO'),
            nzOnOk: () => {
                this.userService.deleteUser(this.user!.id).subscribe({
                    next: () => { this.message.success(this.t('UMS.USERS.USER_DELETED')); this.goToList(); },
                    error: err => this.message.error(extractApiErrorMessage(err) || this.t('UMS.USERS.FAILED_DELETE'))
                });
            }
        });
    }
}
