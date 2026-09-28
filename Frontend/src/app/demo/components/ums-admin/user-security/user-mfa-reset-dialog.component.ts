import { Component, EventEmitter, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription } from 'rxjs';

import { NzModalModule } from 'ng-zorro-antd/modal';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzSpinModule } from 'ng-zorro-antd/spin';

import { TranslatePipe } from '../../../pipes/translate.pipe';
import { ToastService } from '../../../service/toast.service';
import { TranslationService } from '../../../service/translation.service';
import { UmsUserService } from '../../../service/ums-user.service';
import { UmsMfaStatus, UmsUser, UmsUserSummary } from '../../../api/ums';
import { extractApiErrorMessage } from '../../../service/api-error.util';
import { fullNameOf, initialsOf } from '../user-form/user-form.model';

type Subject = UmsUser | UmsUserSummary;

/**
 * Resetting a staff account's second factor (O-16): the dialog behind
 * "Reset two-factor authentication" on the users list and on the account page.
 *
 * <p>It used to be a bare confirm box — a question and a username. That said
 * nothing about what was being removed, and an administrator answering a
 * lost-phone call could not tell whether the account had a factor at all. The
 * dialog now reads the account's status when it opens (enrolled or not,
 * since when, recovery codes left) and says what the reset does before asking
 * for it, in the same layout as every other dialog of this menu.
 *
 * <p>One component hosted twice, like {@link UserAccessDialogsComponent}, so
 * the list and the account page cannot drift. The host calls {@code open(user)}
 * and re-reads what it shows on {@code done}.
 *
 * <p>The server's reset (`MfaService.reset`) deletes the factor row and the
 * recovery codes and evicts the authority cache; it does not sign the account
 * out, and it works on an account that was never enrolled — the audit row then
 * says "not enrolled". The text here states exactly that and no more.
 */
@Component({
    selector: 'app-user-mfa-reset-dialog',
    standalone: true,
    imports: [CommonModule, TranslatePipe, NzModalModule, NzButtonModule, NzIconModule, NzSpinModule],
    template: `
    <nz-modal
      [(nzVisible)]="visible"
      [nzTitle]="'UMS.USERS.RESET_MFA' | translate"
      nzWrapClassName="ums-dialog"
      [nzWidth]="720"
      [nzMaskClosable]="!resetting"
      [nzClosable]="!resetting"
      [nzFooter]="footer"
      (nzOnCancel)="close()">
      <ng-template #footer>
        <div class="ums-dialog__footer">
          <span class="ums-dialog__footer-hint"><i class="pi pi-info-circle"></i> {{ 'UMS.USERS.MFA_RESET.FOOTER_HINT' | translate }}</span>
          <div class="ums-dialog__footer-actions">
            <button nz-button (click)="close()" [disabled]="resetting">{{ 'COMMON.CANCEL' | translate }}</button>
            <button nz-button nzType="primary" nzDanger class="ums-mfa-reset__confirm" (click)="reset()" [nzLoading]="resetting" [disabled]="!subject">
              <span nz-icon nzType="safety"></span>{{ 'UMS.USERS.RESET_MFA' | translate }}
            </button>
          </div>
        </div>
      </ng-template>
      <ng-container *nzModalContent>
        @if (subject) {
          <!-- Who -->
          <div class="dv-hero dv-hero--warn">
            <div class="dv-hero__badge dv-hero__badge--text">{{ initials }}</div>
            <div class="dv-hero__body">
              <div class="dv-hero__eyebrow">{{ 'UMS.USERS.USERNAME' | translate }}</div>
              <div class="dv-hero__title">{{ subject.userName }}</div>
              @if (subjectName) { <div class="dv-hero__subtitle">{{ subjectName }}</div> }
              @if (subject.email) {
                <div class="dv-hero__meta">
                  <span class="dv-chip"><i class="pi pi-envelope"></i>{{ subject.email }}</span>
                </div>
              }
            </div>
          </div>

          <!-- What the account has now. Read on open; a failed read is said,
               not shown as "not enrolled". -->
          <nz-spin [nzSpinning]="statusLoading">
            <div class="dv-kpis dv-kpis--cols-3 ums-mfa-reset__status">
              <div class="dv-kpi" [ngClass]="stateTone">
                <div class="dv-kpi__icon"><i class="pi" [ngClass]="status?.enabled ? 'pi-shield' : 'pi-minus-circle'"></i></div>
                <div class="dv-kpi__body">
                  <div class="dv-kpi__label">{{ 'UMS.USERS.VIEW.MFA' | translate }}</div>
                  <div class="dv-kpi__value">{{ stateLabel | translate }}</div>
                  @if (status?.required && !status?.enabled) {
                    <div class="dv-kpi__sub">{{ 'UMS.USERS.VIEW.MFA_REQUIRED' | translate }}</div>
                  } @else if (status?.locked) {
                    <div class="dv-kpi__sub">{{ 'UMS.USERS.MFA_RESET.LOCKED' | translate }}</div>
                  }
                </div>
              </div>
              <div class="dv-kpi" [ngClass]="status?.enrolledAt ? 'dv-kpi--blue' : 'dv-kpi--gray'">
                <div class="dv-kpi__icon"><i class="pi pi-calendar"></i></div>
                <div class="dv-kpi__body">
                  <div class="dv-kpi__label">{{ 'UMS.USERS.MFA_RESET.ENROLLED_SINCE' | translate }}</div>
                  <div class="dv-kpi__value">
                    @if (statusFailed) { {{ 'COMMON.NOT_AVAILABLE' | translate }} }
                    @else if (status?.enrolledAt) { {{ status!.enrolledAt | date:'mediumDate' }} }
                    @else { {{ 'UMS.USERS.NEVER' | translate }} }
                  </div>
                </div>
              </div>
              <div class="dv-kpi" [ngClass]="(status?.recoveryCodesLeft ?? 0) > 0 ? 'dv-kpi--purple' : 'dv-kpi--gray'">
                <div class="dv-kpi__icon"><i class="pi pi-key"></i></div>
                <div class="dv-kpi__body">
                  <div class="dv-kpi__label">{{ 'UMS.USERS.VIEW.MFA_RECOVERY_LEFT' | translate }}</div>
                  <div class="dv-kpi__value">
                    @if (statusFailed) { {{ 'COMMON.NOT_AVAILABLE' | translate }} }
                    @else { {{ status?.recoveryCodesLeft ?? 0 }} }
                  </div>
                </div>
              </div>
            </div>
          </nz-spin>

          @if (statusFailed) {
            <div class="dv-notice dv-notice--muted ums-mfa-reset__notice">
              <i class="pi pi-exclamation-circle"></i>
              <span>{{ 'UMS.USERS.MFA_RESET.STATUS_FAILED' | translate }}</span>
            </div>
          } @else if (status && !status.enabled) {
            <div class="dv-notice dv-notice--info ums-mfa-reset__notice">
              <i class="pi pi-info-circle"></i>
              <span>{{ 'UMS.USERS.MFA_RESET.NOT_ENROLLED' | translate }}</span>
            </div>
          } @else if (status) {
            <div class="dv-notice dv-notice--warn ums-mfa-reset__notice">
              <i class="pi pi-exclamation-triangle"></i>
              <span>{{ 'UMS.USERS.RESET_MFA_CONFIRM' | translate }}</span>
            </div>
          }

          <!-- What the reset does -->
          <div class="dv-section">
            <div class="dv-section__head">
              <span class="dv-section__icon dv-section__icon--red"><span nz-icon nzType="safety"></span></span>
              <div>
                <div class="dv-section__title">{{ 'UMS.USERS.MFA_RESET.EFFECTS_TITLE' | translate }}</div>
                <div class="dv-section__hint">{{ 'UMS.USERS.MFA_RESET.EFFECTS_HINT' | translate }}</div>
              </div>
            </div>
            <ul class="ums-effects">
              <li>
                <span class="ums-effects__icon ums-effects__icon--red"><i class="pi pi-mobile"></i></span>
                <span>{{ 'UMS.USERS.MFA_RESET.EFFECT_APP' | translate }}</span>
              </li>
              <li>
                <span class="ums-effects__icon ums-effects__icon--red"><i class="pi pi-key"></i></span>
                <span>{{ 'UMS.USERS.MFA_RESET.EFFECT_CODES' | translate }}</span>
              </li>
              <li>
                <span class="ums-effects__icon ums-effects__icon--green"><i class="pi pi-sign-in"></i></span>
                <span>{{ 'UMS.USERS.MFA_RESET.EFFECT_NEXT' | translate }}</span>
              </li>
              <li>
                <span class="ums-effects__icon ums-effects__icon--gray"><i class="pi pi-history"></i></span>
                <span>{{ 'UMS.USERS.MFA_RESET.EFFECT_AUDIT' | translate }}</span>
              </li>
            </ul>
          </div>
        }
      </ng-container>
    </nz-modal>
    `
})
export class UserMfaResetDialogComponent {

    /** The reset went through: the host re-reads what it shows of the account. */
    @Output() done = new EventEmitter<void>();

    /** The account the open dialog is about. */
    subject: Subject | null = null;
    visible = false;

    status: UmsMfaStatus | null = null;
    statusLoading = false;
    /** The status could not be read; the tiles say so and the reset stays available. */
    statusFailed = false;
    resetting = false;

    private statusRequest?: Subscription;

    constructor(
        private userService: UmsUserService,
        private message: ToastService,
        private translationService: TranslationService
    ) { }

    get initials(): string {
        return initialsOf(this.subject);
    }

    get subjectName(): string {
        return fullNameOf(this.subject);
    }

    /** The colour of the state tile: green with a factor, grey without or unknown. */
    get stateTone(): string {
        return this.status?.enabled ? 'dv-kpi--green' : 'dv-kpi--gray';
    }

    get stateLabel(): string {
        if (this.statusFailed) {
            return 'COMMON.NOT_AVAILABLE';
        }
        return this.status?.enabled ? 'UMS.USERS.VIEW.MFA_ENABLED' : 'UMS.USERS.VIEW.MFA_NOT_ENROLLED';
    }

    open(user: Subject): void {
        this.subject = user;
        this.status = null;
        this.statusFailed = false;
        this.resetting = false;
        this.visible = true;
        this.loadStatus(user.id);
    }

    close(): void {
        if (this.resetting) {
            return;
        }
        this.visible = false;
        this.statusRequest?.unsubscribe();
        this.statusLoading = false;
    }

    reset(): void {
        if (!this.subject || this.resetting) {
            return;
        }
        this.resetting = true;
        this.userService.resetMfa(this.subject.id).subscribe({
            next: () => {
                this.resetting = false;
                this.visible = false;
                this.message.success(this.t('UMS.USERS.MFA_RESET_DONE'));
                this.done.emit();
            },
            error: err => {
                this.resetting = false;
                this.message.error(extractApiErrorMessage(err) || this.t('UMS.USERS.FAILED_RESET_MFA'));
            }
        });
    }

    private loadStatus(id: number): void {
        this.statusRequest?.unsubscribe();
        this.statusLoading = true;
        this.statusRequest = this.userService.getMfaStatus(id).subscribe({
            next: status => {
                this.statusLoading = false;
                this.status = status && typeof status === 'object' && 'enabled' in status ? status : null;
                this.statusFailed = !this.status;
            },
            error: () => {
                this.statusLoading = false;
                this.status = null;
                this.statusFailed = true;
            }
        });
    }

    private t(key: string): string {
        return this.translationService.translate(key);
    }
}
