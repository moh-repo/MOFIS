import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzSpinModule } from 'ng-zorro-antd/spin';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { NzBadgeModule } from 'ng-zorro-antd/badge';
import { NzTooltipModule } from 'ng-zorro-antd/tooltip';

import { TranslatePipe } from '../../../pipes/translate.pipe';
import { TranslationService } from '../../../service/translation.service';
import { ToastService } from '../../../service/toast.service';
import { UmsUserService } from '../../../service/ums-user.service';
import { UmsGroupService } from '../../../service/ums-group.service';
import { UmsRoleService } from '../../../service/ums-role.service';
import { UmsAuthService } from '../../../service/ums-auth.service';
import { UmsCountryOption, UmsOption, UmsUserLookupsService } from '../../../service/ums-user-lookups.service';
import { UmsGroup, UmsRole, UmsUser } from '../../../api/ums';
import { changesOnly } from '../../../service/reload-streams.util';
import { extractApiErrorMessage } from '../../../service/api-error.util';
import { UserBasicFieldsComponent } from './user-basic-fields.component';
import {
    PRIVILEGE_ADMIN, StaffUserFormMode, buildStaffUserForm, fillStaffUserForm, fullNameOf, initialsOf,
    markStaffUserFormTouched, requiredList, staffUserCreatePayload, staffUserUpdatePayload, statusBadge
} from './user-form.model';

/**
 * The add and edit pages of a staff account: /ums-admin/users/new and
 * /ums-admin/users/:id/edit.
 *
 * <p>One page in two modes over the one form model. The basic information is
 * {@link UserBasicFieldsComponent}; creation adds the groups and roles to
 * grant, which the server takes as separate writes after the account exists
 * (the attachés travel with the create request, N-02.a). Editing does not
 * touch what is granted: that is the account page's job, where every change
 * writes as it is made.
 *
 * <p>Every dropdown is fed from the database through UmsUserLookupsService;
 * the groups and roles come from their own services.
 */
@Component({
    selector: 'app-user-form',
    standalone: true,
    imports: [
        CommonModule, ReactiveFormsModule, TranslatePipe,
        NzButtonModule, NzIconModule, NzSelectModule, NzSpinModule, NzTagModule, NzBadgeModule, NzTooltipModule,
        UserBasicFieldsComponent
    ],
    template: `
    <div class="ums-admin-page">
      <div class="card">
        <!-- Heading row: the same block as the list, so the three pages read as one place. -->
        <div class="flex align-items-center mb-4 flex-wrap gap-3">
          <div class="flex align-items-center gap-3 flex-1">
            <button nz-button nzType="text" nzShape="circle" class="ums-back" (click)="cancel()" [nz-tooltip]="'COMMON.BACK' | translate">
              <span nz-icon [nzType]="translationService.isRTL() ? 'arrow-right' : 'arrow-left'"></span>
            </button>
            <i class="pi text-primary" [class.pi-user-plus]="isCreate" [class.pi-user-edit]="!isCreate" style="font-size: 2.5rem"></i>
            <div>
              <div class="ums-crumbs">
                <a tabindex="0" (click)="goToList()" (keydown.enter)="goToList()">{{ 'UMS.USERS.TITLE' | translate }}</a>
                <span class="ums-crumbs__sep">/</span>
                @if (!isCreate && user) {
                  <a tabindex="0" (click)="goToView()" (keydown.enter)="goToView()">{{ user.userName }}</a>
                  <span class="ums-crumbs__sep">/</span>
                }
                <span>{{ (isCreate ? 'UMS.USERS.ADD_USER' : 'COMMON.EDIT') | translate }}</span>
              </div>
              <h2 class="m-0 text-primary">{{ (isCreate ? 'UMS.USERS.FORM.NEW_TITLE' : 'UMS.USERS.FORM.EDIT_TITLE') | translate }}</h2>
              <p class="text-600 mt-1 mb-0">{{ (isCreate ? 'UMS.USERS.FORM.NEW_SUBTITLE' : 'UMS.USERS.FORM.EDIT_SUBTITLE') | translate }}</p>
            </div>
          </div>
          <div class="flex gap-2 flex-wrap">
            <button nz-button (click)="cancel()" [disabled]="saving">{{ 'COMMON.CANCEL' | translate }}</button>
            <button nz-button nzType="primary" (click)="save()" [nzLoading]="saving" [disabled]="protected || loading || loadFailed">
              <span nz-icon nzType="save"></span>
              {{ (isCreate ? 'UMS.USERS.FORM.CREATE_ACTION' : 'COMMON.SAVE') | translate }}
            </button>
          </div>
        </div>

        <nz-spin [nzSpinning]="loading">
          @if (loadFailed) {
            <div class="dv-notice dv-notice--danger">
              <i class="pi pi-exclamation-triangle"></i>
              <span>{{ 'UMS.USERS.FAILED_LOAD' | translate }}</span>
              <button nz-button nzSize="small" class="ums-notice__action" (click)="load()">{{ 'COMMON.RETRY' | translate }}</button>
            </div>
          }
          @if (protected) {
            <div class="dv-notice dv-notice--warn">
              <i class="pi pi-lock"></i>
              <span>{{ 'UMS.USERS.PROTECTED_SUPER_ADMIN' | translate }}</span>
            </div>
          }

          <div class="ums-detail-layout">
            <div class="ums-detail-layout__main ums-stack">
              <app-user-basic-fields
                [form]="form"
                [mode]="mode"
                [positionOptions]="positionOptions"
                [statusOptions]="statusOptions"
                [privilegeOptions]="privilegeOptions"
                [countryOptions]="countryOptions"
                [showCountries]="isCreate"
                [countriesRequired]="countriesRequired"
                [superAdmin]="!!user?.isSuperAdmin"
                [canGrantAdmin]="canGrantAdmin">
              </app-user-basic-fields>

              @if (isCreate) {
                <!-- What the account is granted at birth. Groups carry roles; roles carry permissions. -->
                <div class="dv-section" [formGroup]="form">
                  <div class="dv-section__head">
                    <span class="dv-section__icon dv-section__icon--green"><span nz-icon nzType="safety"></span></span>
                    <div>
                      <div class="dv-section__title">{{ 'UMS.USERS.FORM.SECTION_ASSIGNMENTS' | translate }}</div>
                      <div class="dv-section__hint">{{ 'UMS.USERS.FORM.ASSIGNMENTS_HINT' | translate }}</div>
                    </div>
                  </div>
                  <div class="dv-fields">
                    <div class="dv-field">
                      <label class="dv-label" for="sfGroups">{{ 'UMS.USERS.GROUPS' | translate }}</label>
                      <nz-select nzId="sfGroups" formControlName="groupIds" nzMode="multiple" nzShowSearch nzAllowClear [nzMaxTagCount]="6"
                        [nzPlaceHolder]="'UMS.USER_WIZARD.NO_SELECTION' | translate">
                        @for (g of groups; track g.id) {
                          <nz-option [nzValue]="g.id" [nzLabel]="localName(g.groupName, g.groupNameAr)"></nz-option>
                        }
                      </nz-select>
                      <span class="dv-hint">{{ 'UMS.USER_WIZARD.GROUPS_DESC' | translate }}</span>
                    </div>
                    <div class="dv-field">
                      <label class="dv-label" for="sfRoles">{{ 'UMS.USERS.ROLES' | translate }}</label>
                      <nz-select nzId="sfRoles" formControlName="roleIds" nzMode="multiple" nzShowSearch nzAllowClear [nzMaxTagCount]="6"
                        [nzPlaceHolder]="'UMS.USER_WIZARD.NO_SELECTION' | translate">
                        @for (r of roles; track r.id) {
                          <nz-option [nzValue]="r.id" [nzLabel]="localName(r.roleName, r.roleNameAr)"></nz-option>
                        }
                      </nz-select>
                      <span class="dv-hint">{{ 'UMS.USER_WIZARD.ROLES_DESC' | translate }}</span>
                    </div>
                  </div>
                </div>
              } @else {
                <div class="dv-notice dv-notice--muted">
                  <i class="pi pi-info-circle"></i>
                  <span>{{ 'UMS.USERS.FORM.MANAGE_ACCESS_HINT' | translate }}</span>
                  <button nz-button nzSize="small" class="ums-notice__action" (click)="goToView()">
                    <span nz-icon nzType="eye"></span>{{ 'UMS.USERS.FORM.OPEN_ACCOUNT' | translate }}
                  </button>
                </div>
              }
            </div>

            <aside class="ums-detail-layout__aside ums-stack">
              <!-- A live reading of the form: what the account will look like once saved. -->
              <div class="dv-section">
                <div class="dv-section__head">
                  <span class="dv-section__icon dv-section__icon--gray"><span nz-icon nzType="profile"></span></span>
                  <div>
                    <div class="dv-section__title">{{ 'UMS.USERS.FORM.SECTION_SUMMARY' | translate }}</div>
                    <div class="dv-section__hint">{{ (isCreate ? 'UMS.USERS.FORM.SUMMARY_HINT_NEW' : 'UMS.USERS.FORM.SUMMARY_HINT_EDIT') | translate }}</div>
                  </div>
                </div>
                <div class="ums-identity">
                  <div class="ums-identity__badge" [class.ums-identity__badge--admin]="value('privilege') === adminCode">{{ initials }}</div>
                  <div class="ums-identity__body">
                    <div class="ums-identity__name" [class.ums-identity__name--placeholder]="!fullName">{{ fullName || ('UMS.USERS.FULL_NAME' | translate) }}</div>
                    <div class="ums-identity__sub" dir="ltr">{{ value('userName') || '—' }}</div>
                  </div>
                </div>
                <dl class="dv-dl">
                  <div class="dv-dl__row">
                    <dt>{{ 'UMS.USERS.STATUS' | translate }}</dt>
                    <dd><nz-badge [nzStatus]="statusBadge(value('status'))" [nzText]="lookups.statusLabel(value('status')) || value('status')"></nz-badge></dd>
                  </div>
                  <div class="dv-dl__row">
                    <dt>{{ 'UMS.USERS.ACCOUNT_TYPE' | translate }}</dt>
                    <dd>
                      @if (user?.isSuperAdmin) {
                        <nz-tag nzColor="gold">{{ 'UMS.USERS.SUPER_ADMIN' | translate }}</nz-tag>
                      } @else {
                        <nz-tag [nzColor]="value('privilege') === adminCode ? 'blue' : 'default'">{{ lookups.privilegeLabel(value('privilege')) || value('privilege') }}</nz-tag>
                      }
                    </dd>
                  </div>
                  <div class="dv-dl__row">
                    <dt>{{ 'UMS.USERS.POSITION' | translate }}</dt>
                    <dd [class.dv-dl__muted]="!value('positionCode')">{{ lookups.positionLabel(value('positionCode')) || ('UMS.USERS.POSITION_NONE' | translate) }}</dd>
                  </div>
                  <div class="dv-dl__row">
                    <dt>{{ 'UMS.USERS.EMAIL' | translate }}</dt>
                    <dd [class.dv-dl__muted]="!value('email')" dir="ltr">{{ value('email') || ('COMMON.NOT_AVAILABLE' | translate) }}</dd>
                  </div>
                  @if (isCreate) {
                    <div class="dv-dl__row">
                      <dt>{{ 'UMS.USERS.ATTACHES' | translate }}</dt>
                      <dd [class.dv-dl__orange]="!count('countryIds')">{{ count('countryIds') || ('UMS.USERS.AWAITING_ATTACHE' | translate) }}</dd>
                    </div>
                    <div class="dv-dl__row">
                      <dt>{{ 'UMS.USERS.GROUPS' | translate }}</dt>
                      <dd>{{ count('groupIds') }}</dd>
                    </div>
                    <div class="dv-dl__row">
                      <dt>{{ 'UMS.USERS.ROLES' | translate }}</dt>
                      <dd>{{ count('roleIds') }}</dd>
                    </div>
                  }
                </dl>
              </div>

              @if (!isCreate && user) {
                <div class="dv-section">
                  <div class="dv-section__head">
                    <span class="dv-section__icon dv-section__icon--blue"><span nz-icon nzType="history"></span></span>
                    <div><div class="dv-section__title">{{ 'UMS.USERS.FORM.SECTION_FACTS' | translate }}</div></div>
                  </div>
                  <dl class="dv-dl">
                    <div class="dv-dl__row">
                      <dt>{{ 'UMS.USERS.CREATED' | translate }}</dt>
                      <dd>{{ user.createdAt ? (user.createdAt | date:'medium') : ('COMMON.NOT_AVAILABLE' | translate) }}</dd>
                    </div>
                    <div class="dv-dl__row">
                      <dt>{{ 'UMS.USERS.LAST_LOGIN' | translate }}</dt>
                      <dd [class.dv-dl__muted]="!user.lastLoginAt">{{ user.lastLoginAt ? (user.lastLoginAt | date:'medium') : ('UMS.USERS.NEVER' | translate) }}</dd>
                    </div>
                    <div class="dv-dl__row">
                      <dt>{{ 'UMS.USERS.VIEW.PASSWORD_CHANGED' | translate }}</dt>
                      <dd [class.dv-dl__muted]="!user.passwordChangedAt">{{ user.passwordChangedAt ? (user.passwordChangedAt | date:'medium') : ('UMS.USERS.NEVER' | translate) }}</dd>
                    </div>
                  </dl>
                </div>
              }

              <div class="dv-notice dv-notice--info">
                <i class="pi pi-info-circle"></i>
                <span>{{ (isCreate ? 'UMS.USERS.FORM.SAVE_NOTE_NEW' : 'UMS.USERS.FORM.SAVE_NOTE_EDIT') | translate }}</span>
              </div>
            </aside>
          </div>
        </nz-spin>
      </div>
    </div>
    `
})
export class UserFormComponent implements OnInit {

    mode: StaffUserFormMode = 'create';
    userId: number | null = null;
    /** The record being edited; null on the add page. */
    user: UmsUser | null = null;
    form: FormGroup;

    loading = false;
    saving = false;
    loadFailed = false;

    statusOptions: UmsOption[] = [];
    privilegeOptions: UmsOption[] = [];
    countryOptions: UmsCountryOption[] = [];
    groups: UmsGroup[] = [];
    roles: UmsRole[] = [];

    readonly adminCode = PRIVILEGE_ADMIN;
    readonly statusBadge = statusBadge;

    private readonly destroyRef = inject(DestroyRef);
    private readonly authService = inject(UmsAuthService);
    readonly lookups = inject(UmsUserLookupsService);

    constructor(
        private fb: FormBuilder,
        private route: ActivatedRoute,
        private router: Router,
        private userService: UmsUserService,
        private groupService: UmsGroupService,
        private roleService: UmsRoleService,
        private message: ToastService,
        public translationService: TranslationService
    ) {
        this.form = buildStaffUserForm(this.fb, 'create');
    }

    get isCreate(): boolean {
        return this.mode === 'create';
    }

    /** N-03: the positions list, in the reader's language. Synchronous: the map is warm. */
    get positionOptions(): UmsOption[] {
        return this.lookups.positionOptions();
    }

    /** N-02.b: whether this caller may make an administrator. */
    get canGrantAdmin(): boolean {
        return this.authService.mayGrantTier('COUNTRY_ADMIN');
    }

    /** N-02.a: a country administrator must scope a new account to one of its own attachés. */
    get countriesRequired(): boolean {
        if (!this.isCreate) {
            return false;
        }
        const tier = this.authService.adminTier;
        if (typeof tier === 'string') {
            return tier === 'COUNTRY_ADMIN';
        }
        return !this.authService.isSuperAdmin() && this.authService.isMinistryUser !== true && this.authService.isAdmin();
    }

    /** A super admin's account, seen by someone who is not one: the server refuses every write. */
    get protected(): boolean {
        return !!this.user?.isSuperAdmin && !this.authService.isSuperAdmin();
    }

    get initials(): string {
        return initialsOf({ firstName: this.value('firstName'), lastName: this.value('lastName'), userName: this.value('userName') });
    }

    get fullName(): string {
        return fullNameOf({ firstName: this.value('firstName'), lastName: this.value('lastName') });
    }

    value(name: string): any {
        return this.form.get(name)?.value;
    }

    count(name: string): number {
        const v = this.value(name);
        return Array.isArray(v) ? v.length : 0;
    }

    localName(en: string, ar: string | null | undefined): string {
        return this.translationService.getCurrentLanguage() === 'ar' && ar ? ar : en;
    }

    ngOnInit(): void {
        this.loadOptions();
        this.translationService.currentLanguage$.pipe(changesOnly(), takeUntilDestroyed(this.destroyRef))
            .subscribe(() => this.loadOptions());
        // paramMap rather than the snapshot: the router reuses this component
        // between /new and /:id/edit, and between two accounts' edit pages.
        this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
            const raw = params.get('id');
            this.userId = raw ? Number(raw) : null;
            this.mode = this.userId ? 'edit' : 'create';
            this.form = buildStaffUserForm(this.fb, this.mode);
            this.user = null;
            if (this.isCreate && this.countriesRequired) {
                this.form.get('countryIds')?.setValidators([requiredList]);
            }
            this.load();
        });
    }

    private t(key: string): string {
        return this.translationService.translate(key);
    }

    private loadOptions(): void {
        this.lookups.statusOptions().subscribe(opts => this.statusOptions = opts);
        this.lookups.privilegeOptions().subscribe(opts => this.privilegeOptions = opts);
    }

    /** What the page needs from the server: the pick lists, and in edit mode the record. */
    load(): void {
        this.loading = true;
        this.loadFailed = false;
        const soft = <T>(source: any, fallback: T) => (source as any).pipe(catchError(() => of(fallback)));
        forkJoin({
            countries: soft(this.lookups.countries(), [] as UmsCountryOption[]),
            groups: this.isCreate ? soft(this.groupService.getAllActiveGroups(), [] as UmsGroup[]) : of([] as UmsGroup[]),
            roles: this.isCreate ? soft(this.roleService.getAllActiveRoles(), [] as UmsRole[]) : of([] as UmsRole[]),
            user: this.userId ? this.userService.getUserById(this.userId).pipe(catchError(() => of(null))) : of(null)
        }).subscribe(({ countries, groups, roles, user }) => {
            this.countryOptions = Array.isArray(countries) ? countries : [];
            this.groups = Array.isArray(groups) ? groups : [];
            this.roles = (Array.isArray(roles) ? roles : []).filter(r => this.canOfferRole(r));
            if (this.userId) {
                if (!user) {
                    this.loadFailed = true;
                    this.message.error(this.t('UMS.USERS.FAILED_LOAD'));
                } else {
                    this.user = user;
                    fillStaffUserForm(this.form, user);
                    if (user.isSuperAdmin) {
                        // The flag is never set from a form; the type is shown, not chosen.
                        this.form.get('privilege')?.disable();
                    }
                }
            }
            this.loading = false;
        });
    }

    /** N-02.b / N-02.d: a tier role the signed-in account may not grant is not offered. */
    private canOfferRole(role: UmsRole): boolean {
        switch (role.roleName) {
            case 'SUPER_ADMIN': return this.authService.mayGrantTier('SUPER_ADMIN');
            case 'MINISTRY_ADMIN': return this.authService.mayGrantTier('MINISTRY_ADMIN');
            case 'MINISTRY_STAFF': return this.authService.mayGrantTier('MINISTRY_STAFF');
            case 'COUNTRY_ADMIN': return this.authService.mayGrantTier('COUNTRY_ADMIN');
            default: return true;
        }
    }

    save(): void {
        if (this.protected || this.saving) {
            return;
        }
        if (this.form.invalid) {
            markStaffUserFormTouched(this.form);
            this.message.warning(this.t('COMMON.REQUIRED_FIELDS'));
            return;
        }
        this.saving = true;
        if (this.isCreate) {
            this.create();
        } else {
            this.update();
        }
    }

    private create(): void {
        const payload = staffUserCreatePayload(this.form);
        const groupIds: number[] = this.value('groupIds') ?? [];
        const roleIds: number[] = this.value('roleIds') ?? [];
        this.userService.createUser(payload).subscribe({
            next: created => {
                const open = () => {
                    this.saving = false;
                    this.router.navigate(['/ums-admin/users', created.id]);
                };
                // Groups and roles are separate writes once the account exists (the
                // attachés went with the create request, N-02.a).
                const grants = [
                    ...groupIds.map(g => this.userService.assignUserToGroup(created.id, g)),
                    ...roleIds.map(r => this.userService.assignRoleToUser(created.id, r))
                ];
                if (!grants.length) {
                    this.message.success(this.t('UMS.USERS.USER_CREATED'));
                    open();
                    return;
                }
                forkJoin(grants).subscribe({
                    next: () => { this.message.success(this.t('UMS.USER_WIZARD.MESSAGES.USER_CREATED_PERMISSIONS')); open(); },
                    error: () => { this.message.warning(this.t('UMS.USER_WIZARD.MESSAGES.USER_CREATED_PARTIAL')); open(); }
                });
            },
            error: err => {
                this.message.error(extractApiErrorMessage(err) || this.t('UMS.USERS.FAILED_CREATE'));
                this.saving = false;
            }
        });
    }

    private update(): void {
        this.userService.updateUser(this.userId!, staffUserUpdatePayload(this.form)).subscribe({
            next: (saved: any) => {
                if (saved?.pendingApprovalRequestId) {
                    // S-03: the promotion to administrator was split off into a request.
                    this.message.info(this.t('UMS.USERS.PRIVILEGE_CHANGE_PENDING'));
                } else {
                    this.message.success(this.t('UMS.USERS.USER_UPDATED'));
                }
                this.saving = false;
                this.goToView();
            },
            error: err => {
                this.message.error(extractApiErrorMessage(err) || this.t('UMS.USERS.FAILED_UPDATE'));
                this.saving = false;
            }
        });
    }

    cancel(): void {
        if (this.isCreate) {
            this.goToList();
        } else {
            this.goToView();
        }
    }

    goToList(): void {
        this.router.navigate(['/ums-admin/users']);
    }

    goToView(): void {
        if (this.userId) {
            this.router.navigate(['/ums-admin/users', this.userId]);
        } else {
            this.goToList();
        }
    }
}
