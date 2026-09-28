import { Component, DestroyRef, Input, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';

import { NzInputModule } from 'ng-zorro-antd/input';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzIconModule } from 'ng-zorro-antd/icon';

import { TranslatePipe } from '../../../pipes/translate.pipe';
import { UmsCountryOption, UmsOption } from '../../../service/ums-user-lookups.service';
import { PRIVILEGE_ADMIN, StaffUserFormMode } from './user-form.model';

/**
 * The basic information of a staff account as a form: identity, contact,
 * position and access, attachés, and (at creation) the credentials.
 *
 * <p>One component, three hosts — the add page, the edit page and the
 * quick-add dialog on the users list — over the one form group that
 * {@code buildStaffUserForm} builds. Every dropdown is fed from the database
 * by {@code UmsUserLookupsService}; nothing here names a code.
 *
 * <p>The layout is the detail-dialog vocabulary (`.dv-section`, `.dv-fields`,
 * `.dv-field`), so a staff account looks the same being written as it does
 * being read on the account page, and the same as every other record's dialog.
 */
@Component({
    selector: 'app-user-basic-fields',
    standalone: true,
    imports: [CommonModule, ReactiveFormsModule, TranslatePipe, NzInputModule, NzSelectModule, NzIconModule],
    template: `
    <div [formGroup]="form" class="ums-stack">

      <!-- Identity -->
      <div class="dv-section">
        <div class="dv-section__head">
          <span class="dv-section__icon"><span nz-icon nzType="idcard"></span></span>
          <div>
            <div class="dv-section__title">{{ 'UMS.FORM.SECTION_IDENTITY' | translate }}</div>
            <div class="dv-section__hint">{{ 'UMS.USERS.FORM.IDENTITY_HINT' | translate }}</div>
          </div>
        </div>
        <div class="dv-fields dv-fields--cols-3">
          <div class="dv-field">
            <label class="dv-label" [for]="idPrefix + 'UserName'">
              {{ 'UMS.USERS.USERNAME' | translate }}
              @if (mode === 'create') { <span class="dv-required">*</span> }
              @else { <span nz-icon nzType="lock" class="dv-label__lock" [title]="'UMS.USERS.FORM.USERNAME_LOCKED' | translate"></span> }
            </label>
            <input [id]="idPrefix + 'UserName'" nz-input formControlName="userName" dir="ltr" autocomplete="off" maxlength="100"
              [nzStatus]="invalid('userName') ? 'error' : ''"
              [placeholder]="'UMS.USER_WIZARD.USERNAME_PLACEHOLDER' | translate" />
            @if (invalid('userName')) {
              <span class="dv-error"><i class="pi pi-exclamation-circle"></i>{{ errorOf('userName') | translate }}</span>
            } @else if (mode === 'edit') {
              <span class="dv-hint">{{ 'UMS.USERS.FORM.USERNAME_LOCKED' | translate }}</span>
            }
          </div>
          <div class="dv-field">
            <label class="dv-label" [for]="idPrefix + 'FirstName'">{{ 'UMS.USERS.FIRST_NAME' | translate }} <span class="dv-required">*</span></label>
            <input [id]="idPrefix + 'FirstName'" nz-input formControlName="firstName" maxlength="100"
              [nzStatus]="invalid('firstName') ? 'error' : ''" />
            @if (invalid('firstName')) {
              <span class="dv-error"><i class="pi pi-exclamation-circle"></i>{{ errorOf('firstName') | translate }}</span>
            }
          </div>
          <div class="dv-field">
            <label class="dv-label" [for]="idPrefix + 'LastName'">{{ 'UMS.USERS.LAST_NAME' | translate }} <span class="dv-required">*</span></label>
            <input [id]="idPrefix + 'LastName'" nz-input formControlName="lastName" maxlength="100"
              [nzStatus]="invalid('lastName') ? 'error' : ''" />
            @if (invalid('lastName')) {
              <span class="dv-error"><i class="pi pi-exclamation-circle"></i>{{ errorOf('lastName') | translate }}</span>
            }
          </div>
        </div>
      </div>

      <!-- Contact -->
      <div class="dv-section">
        <div class="dv-section__head">
          <span class="dv-section__icon dv-section__icon--blue"><span nz-icon nzType="mail"></span></span>
          <div>
            <div class="dv-section__title">{{ 'UMS.USERS.FORM.SECTION_CONTACT' | translate }}</div>
            <div class="dv-section__hint">{{ 'UMS.USERS.FORM.CONTACT_HINT' | translate }}</div>
          </div>
        </div>
        <div class="dv-fields">
          <div class="dv-field">
            <label class="dv-label" [for]="idPrefix + 'Email'">{{ 'UMS.USERS.EMAIL' | translate }}</label>
            <input [id]="idPrefix + 'Email'" nz-input formControlName="email" type="email" dir="ltr" maxlength="254"
              autocomplete="off" placeholder="name@example.gov"
              [nzStatus]="invalid('email') ? 'error' : ''" />
            @if (invalid('email')) {
              <span class="dv-error"><i class="pi pi-exclamation-circle"></i>{{ errorOf('email') | translate }}</span>
            }
          </div>
          <div class="dv-field">
            <label class="dv-label" [for]="idPrefix + 'Phone'">{{ 'UMS.USERS.PHONE' | translate }}</label>
            <input [id]="idPrefix + 'Phone'" nz-input formControlName="phone" type="tel" dir="ltr" placeholder="+967 7xx xxx xxx"
              [nzStatus]="invalid('phone') ? 'error' : ''" />
            @if (invalid('phone')) {
              <span class="dv-error"><i class="pi pi-exclamation-circle"></i>{{ errorOf('phone') | translate }}</span>
            }
          </div>
        </div>
      </div>

      <!-- Position and access -->
      <div class="dv-section">
        <div class="dv-section__head">
          <span class="dv-section__icon dv-section__icon--purple"><span nz-icon nzType="solution"></span></span>
          <div>
            <div class="dv-section__title">{{ 'UMS.USERS.FORM.SECTION_WORK' | translate }}</div>
            <div class="dv-section__hint">{{ 'UMS.USERS.FORM.WORK_HINT' | translate }}</div>
          </div>
        </div>
        <div class="dv-fields dv-fields--cols-3">
          <div class="dv-field">
            <label class="dv-label" [for]="idPrefix + 'Position'">{{ 'UMS.USERS.POSITION' | translate }}</label>
            <nz-select [nzId]="idPrefix + 'Position'" formControlName="positionCode" nzAllowClear nzShowSearch
              [nzPlaceHolder]="'UMS.USERS.POSITION_NONE' | translate">
              @for (opt of positionOptions; track opt.value) {
                <nz-option [nzValue]="opt.value" [nzLabel]="opt.label"></nz-option>
              }
            </nz-select>
            <span class="dv-hint">{{ 'UMS.USERS.FORM.POSITION_HINT' | translate }}</span>
          </div>
          <div class="dv-field">
            <label class="dv-label" [for]="idPrefix + 'Privilege'">{{ 'UMS.USERS.ACCOUNT_TYPE' | translate }}</label>
            <nz-select [nzId]="idPrefix + 'Privilege'" formControlName="privilege">
              @for (opt of offeredPrivileges; track opt.value) {
                <nz-option [nzValue]="opt.value" [nzLabel]="opt.label"></nz-option>
              }
            </nz-select>
            @if (superAdmin) {
              <span class="dv-hint">{{ 'UMS.USERS.FORM.SUPER_ADMIN_FIXED' | translate }}</span>
            } @else if (mode === 'edit' && form.get('privilege')?.value === adminCode && form.get('privilege')?.dirty) {
              <span class="dv-hint">{{ 'UMS.USERS.FORM.PRIVILEGE_PENDING_NOTE' | translate }}</span>
            } @else {
              <span class="dv-hint">{{ 'UMS.USERS.FORM.ACCOUNT_TYPE_HINT' | translate }}</span>
            }
          </div>
          <div class="dv-field">
            <label class="dv-label" [for]="idPrefix + 'Status'">{{ 'UMS.USERS.STATUS' | translate }}</label>
            <nz-select [nzId]="idPrefix + 'Status'" formControlName="status">
              @for (opt of statusOptions; track opt.value) {
                <nz-option [nzValue]="opt.value" [nzLabel]="opt.label"></nz-option>
              }
            </nz-select>
            <span class="dv-hint">{{ 'UMS.USERS.FORM.STATUS_HINT' | translate }}</span>
          </div>
        </div>
      </div>

      <!-- Attachés -->
      @if (showCountries) {
        <div class="dv-section">
          <div class="dv-section__head">
            <span class="dv-section__icon dv-section__icon--orange"><span nz-icon nzType="global"></span></span>
            <div>
              <div class="dv-section__title">{{ 'UMS.USERS.ATTACHES' | translate }}</div>
              <div class="dv-section__hint">{{ 'UMS.USERS.FORM.ATTACHES_HINT' | translate }}</div>
            </div>
          </div>
          <div class="dv-fields">
            <div class="dv-field dv-field--full">
              <label class="dv-label" [for]="idPrefix + 'Countries'">
                {{ 'UMS.USERS.ATTACHES' | translate }}
                @if (countriesRequired) { <span class="dv-required">*</span> }
              </label>
              <nz-select [nzId]="idPrefix + 'Countries'" formControlName="countryIds" nzMode="multiple" nzShowSearch nzAllowClear
                [nzMaxTagCount]="6" [nzStatus]="invalid('countryIds') ? 'error' : ''"
                [nzPlaceHolder]="'UMS.USERS.AWAITING_ATTACHE' | translate">
                @for (c of countryOptions; track c.id) {
                  <nz-option [nzValue]="c.id" [nzLabel]="c.name"></nz-option>
                }
              </nz-select>
              @if (invalid('countryIds')) {
                <span class="dv-error"><i class="pi pi-exclamation-circle"></i>{{ 'UMS.USERS.FORM.COUNTRIES_REQUIRED' | translate }}</span>
              } @else if (countriesRequired) {
                <span class="dv-hint">{{ 'UMS.USERS.FORM.COUNTRIES_REQUIRED' | translate }}</span>
              }
            </div>
          </div>
        </div>
      }

      <!-- Credentials: creation only -->
      @if (mode === 'create') {
        <div class="dv-section">
          <div class="dv-section__head">
            <span class="dv-section__icon dv-section__icon--red"><span nz-icon nzType="lock"></span></span>
            <div>
              <div class="dv-section__title">{{ 'UMS.USERS.FORM.SECTION_CREDENTIALS' | translate }}</div>
              <div class="dv-section__hint">{{ 'PROFILE.PASSWORD_RULE' | translate }}</div>
            </div>
          </div>
          <div class="dv-fields">
            <div class="dv-field">
              <label class="dv-label" [for]="idPrefix + 'Password'">{{ 'UMS.USERS.PASSWORD' | translate }} <span class="dv-required">*</span></label>
              <input [id]="idPrefix + 'Password'" nz-input formControlName="password" type="password" dir="ltr" autocomplete="new-password"
                [nzStatus]="invalid('password') ? 'error' : ''"
                [placeholder]="'UMS.USER_WIZARD.PASSWORD_PLACEHOLDER' | translate" />
              @if (invalid('password')) {
                <span class="dv-error"><i class="pi pi-exclamation-circle"></i>{{ errorOf('password') | translate }}</span>
              }
            </div>
            <div class="dv-field">
              <label class="dv-label" [for]="idPrefix + 'Confirm'">{{ 'UMS.USER_WIZARD.CONFIRM_PASSWORD' | translate }} <span class="dv-required">*</span></label>
              <input [id]="idPrefix + 'Confirm'" nz-input formControlName="confirmPassword" type="password" dir="ltr" autocomplete="new-password"
                [nzStatus]="invalid('confirmPassword') ? 'error' : ''" />
              @if (invalid('confirmPassword')) {
                <span class="dv-error"><i class="pi pi-exclamation-circle"></i>{{ errorOf('confirmPassword') | translate }}</span>
              }
            </div>
          </div>
          <div class="dv-notice dv-notice--info" style="margin: .9rem 0 0">
            <i class="pi pi-info-circle"></i>
            <span>{{ 'UMS.USERS.FORM.TEMP_PASSWORD_NOTE' | translate }}</span>
          </div>
        </div>
      }
    </div>
    `
})
export class UserBasicFieldsComponent implements OnInit {

    @Input({ required: true }) form!: FormGroup;
    @Input() mode: StaffUserFormMode = 'create';
    @Input() positionOptions: UmsOption[] = [];
    @Input() statusOptions: UmsOption[] = [];
    @Input() privilegeOptions: UmsOption[] = [];
    @Input() countryOptions: UmsCountryOption[] = [];
    /** The attachés section; the edit page manages them from the account page instead. */
    @Input() showCountries = true;
    /** N-02.a: a country administrator must scope a new account to at least one of its attachés. */
    @Input() countriesRequired = false;
    /** The account holds the super-admin flag: its type is not set from here. */
    @Input() superAdmin = false;
    /** N-02.b: a caller who may not make administrators is not offered the type. */
    @Input() canGrantAdmin = true;
    /** Distinct element ids when two hosts are on one page. */
    @Input() idPrefix = 'su';

    readonly adminCode = PRIVILEGE_ADMIN;

    private readonly destroyRef = inject(DestroyRef);

    /** The type list as this caller may use it. */
    get offeredPrivileges(): UmsOption[] {
        return this.canGrantAdmin ? this.privilegeOptions : this.privilegeOptions.filter(o => o.value !== PRIVILEGE_ADMIN);
    }

    ngOnInit(): void {
        // The confirm box is judged against the password box; re-judge it when the password changes.
        const password = this.form.get('password');
        const confirm = this.form.get('confirmPassword');
        if (password && confirm) {
            password.valueChanges.pipe(takeUntilDestroyed(this.destroyRef))
                .subscribe(() => confirm.updateValueAndValidity({ onlySelf: true }));
        }
    }

    invalid(name: string): boolean {
        const control = this.form.get(name);
        return !!control && control.invalid && (control.dirty || control.touched);
    }

    /** The i18n key of the first error on a control, in the order a reader would want them. */
    errorOf(name: string): string {
        const errors = this.form.get(name)?.errors ?? {};
        if (errors['required']) { return 'COMMON.REQUIRED_FIELD'; }
        if (errors['email']) { return 'COMMON.INVALID_EMAIL'; }
        if (errors['phone']) { return 'COMMON.INVALID_PHONE'; }
        if (errors['mismatch']) { return 'UMS.USER_WIZARD.PASSWORDS_MISMATCH'; }
        if (name === 'password' && (errors['minlength'] || errors['pattern'])) { return 'PROFILE.PASSWORD_RULE'; }
        if (name === 'userName' && errors['minlength']) { return 'UMS.USERS.FORM.USERNAME_RULE'; }
        if (errors['maxlength']) { return 'UMS.USERS.FORM.TOO_LONG'; }
        return 'COMMON.VALIDATION_ERROR';
    }
}
