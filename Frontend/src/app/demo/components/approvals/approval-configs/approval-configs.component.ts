import { Component, OnInit } from '@angular/core';
import { ApprovalService, ApprovalConfig, ApprovalLevel } from '../../../service/approval.service';
import { MessageService, ConfirmationService } from 'primeng/api';
import { TranslationService } from '../../../service/translation.service';
import { UmsPermissionService } from '../../../service/ums-permission.service';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../../environments/environment';
import { CodeListService } from '../../../service/code-list.service';
import { UmsUserOption } from '../../../api/ums';

/** The code `APPROVAL_CONFIG_WRITE` (MoheSecurityExpressions) accepts for every approval-rule write. */
export const APPROVAL_CONFIG_WRITE = 'APP_CONFIG.EDIT';

@Component({
    selector: 'app-approval-configs',
    providers: [MessageService, ConfirmationService],
    template: `
        <p-toast></p-toast>
        <p-confirmdialog></p-confirmdialog>
        
        <div class="card">
          <!--
            Header. The icon-and-title block takes the free space rather than the
            row carrying justify-content-between: styles.scss reverses that class
            again for RTL, which flipped the title back to the left in Arabic.

            The icon is the other half of it. Every other page in the application
            leads its heading with one; these four led with the words alone, so
            the Approvals menu was the odd one out at a glance.
          -->
          <div class="flex align-items-center mb-4 flex-wrap gap-3">
            <div class="flex align-items-center gap-3 flex-1">
              <i class="pi pi-sitemap text-primary" style="font-size: 2.5rem"></i>
              <div>
                <h2 class="m-0 text-primary">{{ 'APPROVAL.configs' | translate }}</h2>
                <p class="text-600 mt-1 mb-0">{{ 'APPROVAL.manageWorkflows' | translate }}</p>
              </div>
            </div>
            @if (canAdd) {
              <button pButton icon="pi pi-plus" [label]="'APPROVAL.addConfiguration' | translate"
                class="p-button-success"
              (click)="openNewConfigDialog()"></button>
            }
          </div>

          <!--
            Filters. Shared layout: assets/layout/styles/_filter-bar.scss, the
            same row the other five pages of this menu carry.

            This narrows the list already in hand rather than asking the server
            again: getAllConfigs() returns every configuration and the
            controller offers no filtering, which is the right trade at twelve
            rows.
          -->
          <div class="filter-bar">
            <div class="filter-bar__grid filter-bar__grid--cols-3">
              <div class="filter-bar__field" [class.filter-bar__field--active]="filters.text">
                <label for="acText">{{ 'APPROVAL.entityType' | translate }}</label>
                <input id="acText" pInputText [(ngModel)]="filters.text"
                  [placeholder]="'APPROVAL.entityTypePlaceholder' | translate"
                  (ngModelChange)="applyFilters()"
                  (keydown.enter)="applyFilters()">
              </div>
              <div class="filter-bar__field" [class.filter-bar__field--active]="filters.levels !== null">
                <label for="acLevels">{{ 'APPROVAL.totalLevels' | translate }}</label>
                <p-select inputId="acLevels" [options]="levelOptions" [(ngModel)]="filters.levels"
                  [placeholder]="'APPROVAL.totalLevels' | translate"
                  [showClear]="true" optionLabel="label" optionValue="value"
                  (onChange)="applyFilters()"></p-select>
              </div>
              <div class="filter-bar__field" [class.filter-bar__field--active]="filters.active !== null">
                <label for="acActive">{{ 'APPROVAL.statusLabel' | translate }}</label>
                <p-select inputId="acActive" [options]="activeOptions" [(ngModel)]="filters.active"
                  [placeholder]="'APPROVAL.statusLabel' | translate"
                  [showClear]="true" optionLabel="label" optionValue="value"
                  (onChange)="applyFilters()"></p-select>
              </div>
            </div>

            <div class="filter-bar__actions">
              <button pButton icon="pi pi-times" [label]="'COMMON.RESET' | translate"
                class="p-button-sm p-button-action-outlined"
                [disabled]="!hasFilters" (click)="resetFilters()"></button>
              <span class="filter-bar__hint filter-bar__actions-spacer">
                {{ 'COMMON.TOTAL' | translate }}: {{ filteredConfigs.length }}
              </span>
            </div>
          </div>

          <p-table [value]="filteredConfigs" [loading]="loading" styleClass="p-datatable-sm p-datatable-striped"
            responsiveLayout="scroll"
            [paginator]="true" [(first)]="tableFirst" [rows]="tableRows"
            [rowsPerPageOptions]="rowsPerPageOptions"
            [showCurrentPageReport]="true"
            [currentPageReportTemplate]="reportTemplate">
            <ng-template pTemplate="header">
              <tr>
                <th pSortableColumn="entityType">{{ 'APPROVAL.entityType' | translate }} <p-sortIcon field="entityType"></p-sortIcon></th>
                <th pSortableColumn="description">{{ 'APPROVAL.description' | translate }} <p-sortIcon field="description"></p-sortIcon></th>
                <th pSortableColumn="totalLevels">{{ 'APPROVAL.totalLevels' | translate }} <p-sortIcon field="totalLevels"></p-sortIcon></th>
                <th pSortableColumn="isActive">{{ 'APPROVAL.isActive' | translate }} <p-sortIcon field="isActive"></p-sortIcon></th>
                <th style="width: 150px">{{ 'APPROVAL.actions' | translate }}</th>
              </tr>
            </ng-template>
        
            <ng-template pTemplate="body" let-config>
              <tr>
                <td><strong>{{ config.entityType }}</strong></td>
                <td>{{ configDescription(config) }}</td>
                <td>
                  <p-tag [value]="config.totalLevels + ' ' + ('APPROVAL.levels' | translate)" severity="info"></p-tag>
                </td>
                <td>
                  <p-tag [value]="config.isActive ? ('APPROVAL.active' | translate) : ('APPROVAL.inactive' | translate)"
                  [severity]="config.isActive ? 'success' : 'danger'"></p-tag>
                </td>
                <td>
                  <div class="flex gap-1">
                    <button pButton icon="pi pi-list"
                      class="p-button-info p-button-text p-button-sm"
                      [pTooltip]="'APPROVAL.manageLevels' | translate"
                    (click)="openLevelsDialog(config)"></button>
                    @if (canEdit) {
                      <button pButton icon="pi pi-pencil"
                        class="p-button-text p-button-sm"
                        [pTooltip]="'APPROVAL.edit' | translate"
                      (click)="openEditDialog(config)"></button>
                    }
                    @if (canDelete && config.isActive) {
                      <button pButton icon="pi pi-trash"
                        class="p-button-danger p-button-text p-button-sm"
                        [pTooltip]="'APPROVAL.deactivate' | translate"
                      (click)="confirmDeactivate(config)"></button>
                    }
                  </div>
                </td>
              </tr>
            </ng-template>
        
            <ng-template pTemplate="emptymessage">
              <tr>
                <td colspan="5" class="text-center p-4">
                  <i class="pi pi-cog text-4xl text-300 mb-3"></i>
                  <p class="text-500">{{ 'APPROVAL.noConfigsFound' | translate }}</p>
                </td>
              </tr>
            </ng-template>
          </p-table>
        </div>
        
        <!--
          Configuration. Shared vocabulary: assets/layout/styles/_detail-dialog.scss.

          It was a bare p-fluid column of .field blocks whose labels carried a
          literal asterisk, and Save was disabled until the form was valid — which
          says that something is wrong without saying what, and leaves a dead
          button as the only signal. Save is live now and each required field
          carries its own message.
        -->
        <p-dialog [(visible)]="showConfigDialog"
          [header]="(editingConfig?.id ? 'APPROVAL.editConfiguration' : 'APPROVAL.newConfiguration') | translate"
          [modal]="true" [draggable]="false"
          [style]="{ width: '600px', maxWidth: '96vw' }" styleClass="dv-dialog"
          (onHide)="configSubmitted = false">
          <ng-template pTemplate="content">
            @if (editingConfig) {
              <div class="dv-hero" [class.dv-hero--new]="!editingConfig.id">
                <div class="dv-hero__badge">
                  @if (editingConfig.id) { {{ editingConfig.id }} } @else { <i class="pi pi-plus"></i> }
                </div>
                <div class="dv-hero__body">
                  <div class="dv-hero__eyebrow">
                    {{ (editingConfig.id ? 'APPROVAL.editConfiguration' : 'APPROVAL.newConfiguration') | translate }}
                  </div>
                  <div class="dv-hero__title" [class.dv-hero__title--placeholder]="!editingConfig.entityType">
                    {{ editingConfig.entityType || ('APPROVAL.entityTypeLabel' | translate) }}
                  </div>
                  <div class="dv-hero__meta">
                    <span class="dv-chip">
                      {{ editingConfig.totalLevels }} {{ 'APPROVAL.levels' | translate }}
                    </span>
                    <p-tag [rounded]="true" [severity]="editingConfig.isActive ? 'success' : 'danger'"
                      [value]="(editingConfig.isActive ? 'APPROVAL.active' : 'APPROVAL.inactive') | translate"></p-tag>
                  </div>
                </div>
              </div>

              <section class="dv-section">
                <header class="dv-section__head">
                  <span class="dv-section__icon dv-section__icon--blue"><i class="pi pi-sitemap"></i></span>
                  <div><div class="dv-section__title">{{ 'APPROVAL.sectionConfiguration' | translate }}</div></div>
                </header>
                <div class="dv-fields">
                  <div class="dv-field">
                    <label for="entityType" class="dv-label">
                      {{ 'APPROVAL.entityTypeLabel' | translate }} <span class="dv-required">*</span>
                    </label>
                    <input pInputText id="entityType" class="w-full" dir="ltr"
                      [(ngModel)]="editingConfig.entityType" [disabled]="!!editingConfig.id"
                      [placeholder]="'APPROVAL.entityTypePlaceholder' | translate" />
                    <small class="dv-hint">{{ 'APPROVAL.cannotChangeAfterCreation' | translate }}</small>
                    @if (configSubmitted && !editingConfig.entityType?.trim()) {
                      <small class="dv-error">
                        <i class="pi pi-exclamation-circle"></i> {{ 'COMMON.REQUIRED_FIELD' | translate }}
                      </small>
                    }
                  </div>
                  <div class="dv-field">
                    <label for="totalLevels" class="dv-label">{{ 'APPROVAL.totalLevels' | translate }}</label>
                    <p-inputnumber inputId="totalLevels" [(ngModel)]="editingConfig.totalLevels"
                      [min]="1" [max]="10" [showButtons]="true"
                      styleClass="w-full" inputStyleClass="w-full"></p-inputnumber>
                    <small class="dv-hint">{{ 'APPROVAL.numberOfLevels' | translate }}</small>
                  </div>
                  <div class="dv-field dv-field--full">
                    <label for="description" class="dv-label">{{ 'APPROVAL.description' | translate }}</label>
                    <input pInputText id="description" class="w-full" dir="ltr"
                      [(ngModel)]="editingConfig.description"
                      [placeholder]="'APPROVAL.briefDescription' | translate" />
                  </div>
                  <!--
                    Both languages are edited here. The list shows whichever one
                    matches the language being read, and DescriptionAr was
                    reachable only through the database until now - which is why
                    one of the twelve workflows still has none.
                  -->
                  <div class="dv-field dv-field--full">
                    <label for="descriptionAr" class="dv-label">{{ 'APPROVAL.descriptionAr' | translate }}</label>
                    <input pInputText id="descriptionAr" class="w-full" dir="rtl"
                      [(ngModel)]="editingConfig.descriptionAr"
                      [placeholder]="'APPROVAL.briefDescriptionAr' | translate" />
                  </div>
                  <div class="dv-field dv-field--full">
                    <label for="isActive" class="dv-label">{{ 'APPROVAL.isActive' | translate }}</label>
                    <div class="flex align-items-center gap-2">
                      <p-checkbox [(ngModel)]="editingConfig.isActive" [binary]="true" inputId="isActive"></p-checkbox>
                      <label for="isActive" class="text-sm">{{ 'APPROVAL.manageWorkflows' | translate }}</label>
                    </div>
                  </div>
                </div>
              </section>
            }
          </ng-template>
          <ng-template pTemplate="footer">
            <div class="dv-footer">
              <small class="dv-footer__hint">
                <span class="dv-required">*</span> {{ 'COMMON.REQUIRED_FIELD' | translate }}
              </small>
              <div class="dv-footer__actions">
                <button pButton type="button" class="p-button-text p-button-secondary" icon="pi pi-times"
                  [label]="'APPROVAL.cancel' | translate" [disabled]="saving"
                  (click)="showConfigDialog = false"></button>
                <button pButton type="button" icon="pi pi-check" [label]="'APPROVAL.save' | translate"
                  [loading]="saving" (click)="saveConfig()"></button>
              </div>
            </div>
          </ng-template>
        </p-dialog>

        <!-- The levels of one workflow, in the order they are asked for. -->
        <p-dialog [(visible)]="showLevelsDialog"
          [header]="('APPROVAL.approvalLevels' | translate) + ' — ' + (selectedConfig?.entityType || '')"
          [modal]="true" [draggable]="false" [maximizable]="true"
          [style]="{ width: '760px', maxWidth: '96vw' }" styleClass="dv-dialog">
          <ng-template pTemplate="content">
            @if (selectedConfig) {
              <div class="dv-view">
                <div class="dv-hero">
                  <div class="dv-hero__badge"><i class="pi pi-list"></i></div>
                  <div class="dv-hero__body">
                    <div class="dv-hero__eyebrow">{{ 'APPROVAL.approvalLevels' | translate }}</div>
                    <div class="dv-hero__title">{{ selectedConfig.entityType }}</div>
                    <div class="dv-hero__meta">
                      @if (selectedConfig.description) {
                        <span class="dv-chip">{{ selectedConfig.description }}</span>
                      }
                      <span class="dv-chip">
                        {{ selectedConfig.totalLevels }} {{ 'APPROVAL.levels' | translate }}
                      </span>
                    </div>
                  </div>
                </div>

                <section class="dv-section">
                  <!--
                    flex-1 on the leading block rather than
                    justify-content-between: styles.scss reverses that class again
                    under RTL, which put the hint on the right and Add level on the
                    left in Arabic — the same double mirror as the page headings.
                  -->
                  <header class="dv-section__head">
                    <span class="dv-section__icon dv-section__icon--purple"><i class="pi pi-sort"></i></span>
                    <div class="flex-1">
                      <div class="dv-section__title">{{ 'APPROVAL.sectionLevel' | translate }}</div>
                      <div class="dv-section__hint">{{ 'APPROVAL.dragToReorder' | translate }}</div>
                    </div>
                    @if (canEdit) {
                      <button pButton icon="pi pi-plus" [label]="'APPROVAL.addLevel' | translate"
                        class="p-button-sm p-button-success" (click)="openNewLevelDialog()"></button>
                    }
                  </header>

                  <p-orderList [value]="levels" [listStyle]="{'max-height':'400px'}"
                    [dragdrop]="true" (onReorder)="onLevelReorder()">
                    <ng-template let-level pTemplate="item">
                      <div class="flex align-items-center w-full p-2 gap-3">
                        <div class="flex align-items-center gap-3 flex-1">
                          <span class="font-bold text-primary">{{ 'APPROVAL.level' | translate }} {{ level.levelOrder }}</span>
                          <span>{{ level.levelName }}</span>
                          @if (level.requiredRoleId) {
                            <p-tag severity="secondary" [rounded]="true"
                              [value]="('APPROVAL.requiredRole' | translate) + ': ' + roleName(level.requiredRoleId)"></p-tag>
                          }
                          @if (level.requiredPositionCode) {
                            <p-tag severity="info" [value]="('APPROVAL.requiredPosition' | translate) + ': ' + positionLabel(level.requiredPositionCode)"></p-tag>
                          }
                          @if (level.namedApprovers?.length) {
                            <p-tag severity="secondary" [value]="('APPROVAL.namedApprovers' | translate) + ': ' + namedApproverNames(level)"></p-tag>
                          }
                        </div>
                        <div class="flex gap-1">
                          @if (canEdit) {
                            <button pButton icon="pi pi-pencil" class="p-button-text p-button-sm"
                              (click)="openEditLevelDialog(level)"
                              [pTooltip]="'COMMON.EDIT' | translate" [attr.aria-label]="'COMMON.EDIT' | translate"></button>
                          }
                          @if (canDelete) {
                            <button pButton icon="pi pi-trash" class="p-button-danger p-button-text p-button-sm"
                              (click)="confirmDeleteLevel(level)"
                              [pTooltip]="'COMMON.DELETE' | translate" [attr.aria-label]="'COMMON.DELETE' | translate"></button>
                          }
                        </div>
                      </div>
                    </ng-template>
                  </p-orderList>

                  @if (levels.length === 0) {
                    <div class="dv-notice dv-notice--muted">
                      <i class="pi pi-info-circle"></i>
                      <span>{{ 'APPROVAL.noLevelsConfigured' | translate }}</span>
                    </div>
                  }
                </section>
              </div>
            }
          </ng-template>
          <ng-template pTemplate="footer">
            <div class="dv-footer">
              <div class="dv-footer__actions">
                <button pButton type="button" class="p-button-text p-button-secondary" icon="pi pi-times"
                  [label]="'APPROVAL.close' | translate" (click)="showLevelsDialog = false"></button>
              </div>
            </div>
          </ng-template>
        </p-dialog>

        <!-- One level: what it is called and who signs it off. -->
        <p-dialog [(visible)]="showLevelDialog"
          [header]="(editingLevel?.id ? 'APPROVAL.edit' : 'APPROVAL.addLevel') | translate"
          [modal]="true" [draggable]="false"
          [style]="{ width: '540px', maxWidth: '96vw' }" styleClass="dv-dialog"
          (onHide)="levelSubmitted = false">
          <ng-template pTemplate="content">
            @if (editingLevel) {
              <div class="dv-hero" [class.dv-hero--new]="!editingLevel.id">
                <div class="dv-hero__badge">
                  @if (editingLevel.id) { {{ editingLevel.levelOrder }} } @else { <i class="pi pi-plus"></i> }
                </div>
                <div class="dv-hero__body">
                  <div class="dv-hero__eyebrow">{{ 'APPROVAL.sectionLevel' | translate }}</div>
                  <div class="dv-hero__title" [class.dv-hero__title--placeholder]="!editingLevel.levelName">
                    {{ editingLevel.levelName || ('APPROVAL.levelName' | translate) }}
                  </div>
                  <div class="dv-hero__meta">
                    @if (editingLevel.requiredRoleId) {
                      <span class="dv-chip">{{ roleName(editingLevel.requiredRoleId) }}</span>
                    }
                    @if (editingLevel.requiredPositionCode) {
                      <span class="dv-chip">{{ positionLabel(editingLevel.requiredPositionCode) }}</span>
                    }
                  </div>
                </div>
              </div>

              <section class="dv-section">
                <header class="dv-section__head">
                  <span class="dv-section__icon dv-section__icon--green"><i class="pi pi-verified"></i></span>
                  <div><div class="dv-section__title">{{ 'APPROVAL.sectionLevel' | translate }}</div></div>
                </header>
                <div class="dv-fields">
                  <div class="dv-field dv-field--full">
                    <label for="levelName" class="dv-label">
                      {{ 'APPROVAL.levelName' | translate }} <span class="dv-required">*</span>
                    </label>
                    <input pInputText id="levelName" class="w-full" [(ngModel)]="editingLevel.levelName"
                      [placeholder]="'APPROVAL.levelNamePlaceholder' | translate" />
                    @if (levelSubmitted && !editingLevel.levelName?.trim()) {
                      <small class="dv-error">
                        <i class="pi pi-exclamation-circle"></i> {{ 'COMMON.REQUIRED_FIELD' | translate }}
                      </small>
                    }
                  </div>
                  <div class="dv-field dv-field--full">
                    <!-- A role, a position or both (P5-5, D25): the users holding the position
                         approve the level; with both, a user must hold both. -->
                    <label for="requiredRoleId" class="dv-label">{{ 'APPROVAL.requiredRole' | translate }}</label>
                    <p-select inputId="requiredRoleId" [options]="roleOptions" [(ngModel)]="editingLevel.requiredRoleId"
                      optionLabel="label" optionValue="value" appendTo="body" styleClass="w-full" [showClear]="true"
                      [placeholder]="'APPROVAL.selectRequiredRole' | translate"></p-select>
                    @if (levelSubmitted && !levelNamesWhoApproves()) {
                      <small class="dv-error">
                        <i class="pi pi-exclamation-circle"></i> {{ 'APPROVAL.roleOrPositionRequired' | translate }}
                      </small>
                    }
                  </div>
                  <div class="dv-field dv-field--full">
                    <label for="requiredPositionCode" class="dv-label">{{ 'APPROVAL.requiredPosition' | translate }}</label>
                    <p-select inputId="requiredPositionCode" [options]="positionOptions" [(ngModel)]="editingLevel.requiredPositionCode"
                      optionLabel="label" optionValue="value" appendTo="body" styleClass="w-full" [showClear]="true"
                      [placeholder]="'APPROVAL.anyPosition' | translate" (onChange)="loadCandidates()"></p-select>
                    <small class="text-500">{{ 'APPROVAL.requiredPositionHint' | translate }}</small>
                  </div>
                  <div class="dv-field dv-field--full">
                    <label class="dv-label">{{ 'APPROVAL.namedApprovers' | translate }}</label>
                    <small class="text-500 block mb-2">{{ 'APPROVAL.namedApproversHint' | translate }}</small>
                    @if (candidates.length === 0) {
                      <small class="text-500">{{ 'APPROVAL.noCandidates' | translate }}</small>
                    }
                    @for (c of candidates; track c.id) {
                      <div class="flex align-items-center gap-2 py-1">
                        <p-checkbox [binary]="true" [ngModel]="isNamed(c.id)" (ngModelChange)="toggleNamed(c, $event)"
                          [inputId]="'appr-' + c.id"></p-checkbox>
                        <label [for]="'appr-' + c.id" class="text-sm">
                          {{ c.userName }} — {{ c.firstName }} {{ c.lastName }}
                          <span class="text-500">({{ (c.countryNames || []).join(', ') || ('APPROVAL.allCountries' | translate) }})</span>
                        </label>
                      </div>
                    }
                  </div>
                  @if (editingLevel.requiredPositionCode && candidatesByCountry.length) {
                    <div class="dv-field dv-field--full">
                      <label class="dv-label">{{ 'APPROVAL.approversPerCountry' | translate }}</label>
                      <table class="w-full text-sm">
                        <tbody>
                          @for (row of candidatesByCountry; track row.country) {
                            <tr><td class="py-1 pr-3 font-medium">{{ row.country }}</td><td>{{ row.names.join(', ') }}</td></tr>
                          }
                        </tbody>
                      </table>
                    </div>
                  }
                  <div class="dv-field dv-field--full">
                    <label for="canSkip" class="dv-label">{{ 'APPROVAL.canSkip' | translate }}</label>
                    <div class="flex align-items-center gap-2">
                      <p-checkbox [(ngModel)]="editingLevel.canSkip" [binary]="true" inputId="canSkip"></p-checkbox>
                      <label for="canSkip" class="text-sm">{{ 'APPROVAL.canSkip' | translate }}</label>
                    </div>
                  </div>
                </div>
              </section>
            }
          </ng-template>
          <ng-template pTemplate="footer">
            <div class="dv-footer">
              <small class="dv-footer__hint">
                <span class="dv-required">*</span> {{ 'COMMON.REQUIRED_FIELD' | translate }}
              </small>
              <div class="dv-footer__actions">
                <button pButton type="button" class="p-button-text p-button-secondary" icon="pi pi-times"
                  [label]="'APPROVAL.cancel' | translate" [disabled]="savingLevel"
                  (click)="showLevelDialog = false"></button>
                <button pButton type="button" icon="pi pi-check" [label]="'APPROVAL.save' | translate"
                  [loading]="savingLevel" (click)="saveLevel()"></button>
              </div>
            </div>
          </ng-template>
        </p-dialog>
        `,
    styles: [`
        :host ::ng-deep .p-orderlist .p-orderlist-list .p-orderlist-item {
            padding: 0;
        }
    `],
    standalone: false
})
export class ApprovalConfigsComponent implements OnInit {
    configs: ApprovalConfig[] = [];
    levels: ApprovalLevel[] = [];
    loading = false;
    saving = false;
    savingLevel = false;

    showConfigDialog = false;
    showLevelsDialog = false;
    showLevelDialog = false;

    /**
     * Set when Save is pressed. Both dialogs used to disable Save until the form
     * was valid, which says that something is wrong without saying what and
     * leaves a dead button as the only signal.
     */
    configSubmitted = false;
    levelSubmitted = false;

    editingConfig: Partial<ApprovalConfig> | null = null;
    selectedConfig: ApprovalConfig | null = null;
    editingLevel: Partial<ApprovalLevel> | null = null;

    // Role options loaded from database
    roleOptions: { label: string; value: number }[] = [];

    /**
     * What the filter row holds. `active` is a tri-state: null is "either".
     */
    filters: { text: string; active: boolean | null; levels: number | null } =
        { text: '', active: null, levels: null };

    /**
     * The rows the table shows. Precomputed on every change rather than a
     * getter, so change detection is not re-filtering the list on each pass.
     */
    filteredConfigs: ApprovalConfig[] = [];

    /** Active / inactive, as the table's own tag names them. */
    activeOptions: { label: string; value: boolean }[] = [];

    /**
     * The level counts to choose from, built from the configurations in hand
     * rather than 1..10 - offering a count no workflow has could only ever
     * return an empty table.
     */
    levelOptions: { label: string; value: number }[] = [];

    tableFirst = 0;
    tableRows = 10;
    rowsPerPageOptions = [10, 25, 50];

    /**
     * The role's name, not its id. The levels list showed `Required role: 7`,
     * which names nothing a person configuring a workflow can act on.
     */
    roleName(roleId: number | null | undefined): string {
        if (roleId == null) { return this.translationService.translate('COMMON.NOT_AVAILABLE'); }
        return this.roleOptions.find(r => r.value === roleId)?.label ?? String(roleId);
    }

    // ---- N-03: positions and named approvers, from the staff table ---------

    /** The positions list, in the reader's language. */
    get positionOptions(): Array<{ label: string; value: string }> {
        return this.codeLists.options('position', this.translationService.getCurrentLanguage());
    }

    positionLabel(code: string | null | undefined): string {
        if (!code) { return ''; }
        return this.codeLists.label('position', code, this.translationService.getCurrentLanguage()) || code;
    }

    namedApproverNames(level: ApprovalLevel): string {
        return (level.namedApprovers || []).map(a => a.userName || String(a.userId)).join(', ');
    }

    /** Active staff who could be named on the level being edited (holding its position, if one is set). */
    candidates: UmsUserOption[] = [];

    /** For a level with a position: who resolves to it in each country. */
    get candidatesByCountry(): { country: string; names: string[] }[] {
        const byCountry = new Map<string, string[]>();
        for (const c of this.candidates) {
            const countries = c.countryNames?.length ? c.countryNames : [this.translationService.translate('APPROVAL.allCountries')];
            for (const name of countries) {
                if (!byCountry.has(name)) { byCountry.set(name, []); }
                byCountry.get(name)!.push(c.userName);
            }
        }
        return Array.from(byCountry.entries()).map(([country, names]) => ({ country, names })).sort((a, b) => a.country.localeCompare(b.country));
    }

    loadCandidates(): void {
        const position = this.editingLevel?.requiredPositionCode || null;
        let params: Record<string, string> = {};
        if (position) { params = { positionCode: position }; }
        this.httpClient.get<UmsUserOption[]>(`${environment.apiUrl}/mohe/api/v1/ums/users/approver-candidates`, { params }).subscribe({
            next: users => { this.candidates = Array.isArray(users) ? users : []; },
            error: () => { this.candidates = []; }
        });
    }

    isNamed(userId: number): boolean {
        return (this.editingLevel?.namedApprovers || []).some(a => a.userId === userId);
    }

    toggleNamed(user: UmsUserOption, checked: boolean): void {
        if (!this.editingLevel) { return; }
        const current = this.editingLevel.namedApprovers || [];
        this.editingLevel.namedApprovers = checked
            ? [...current.filter(a => a.userId !== user.id), { userId: user.id, countryId: null, userName: user.userName }]
            : current.filter(a => a.userId !== user.id);
    }

    canAdd: boolean = false;
    canEdit: boolean = false;
    canDelete: boolean = false;

    constructor(
        private approvalService: ApprovalService,
        private messageService: MessageService,
        private confirmationService: ConfirmationService,
        private translationService: TranslationService,
        private permissionService: UmsPermissionService,
        private httpClient: HttpClient,
        private codeLists: CodeListService
    ) {
        // One code, the one the server checks on every approval-rule write (FIX-PLAN P5-5):
        // APP_CONFIG.ADD and .DELETE are on no role, so only the super admin saw Add and Delete.
        const mayWrite = this.permissionService.hasPermission(APPROVAL_CONFIG_WRITE);
        this.canAdd = mayWrite;
        this.canEdit = mayWrite;
        this.canDelete = mayWrite;
    }

    /** A level names who approves it by a role, a position or both (P5-5). */
    levelNamesWhoApproves(): boolean {
        return !!this.editingLevel?.requiredRoleId || !!this.editingLevel?.requiredPositionCode;
    }

    ngOnInit(): void {
        this.refreshActiveOptions();
        this.loadRoles();
        this.loadConfigs();
    }

    private refreshLevelOptions(): void {
        const counts = Array.from(new Set(
            (this.configs ?? []).map(c => c.totalLevels).filter(n => n != null)
        )).sort((a, b) => a - b);
        const levels = this.translationService.translate('APPROVAL.levels');
        this.levelOptions = counts.map(value => ({ label: `${value} ${levels}`, value }));
    }

    private refreshActiveOptions(): void {
        this.activeOptions = [
            { label: this.translationService.translate('APPROVAL.active'), value: true },
            { label: this.translationService.translate('APPROVAL.inactive'), value: false }
        ];
    }

    /**
     * Narrows the list already loaded.
     *
     * <p>The text matches the entity type or the description, because the code
     * is SHOUTING_SNAKE_CASE while the words a person would search for are in
     * the description beside it. A row with no description at all is still
     * matched on its code.
     */
    applyFilters(): void {
        const text = (this.filters.text ?? '').trim().toLowerCase();
        const active = this.filters.active;
        const levels = this.filters.levels;
        this.filteredConfigs = (this.configs ?? []).filter(config => {
            const haystack = [config.entityType, this.configDescription(config)]
                .filter(Boolean).join(' ').toLowerCase();
            const matchesText = !text || haystack.includes(text);
            const matchesActive = active === null || config.isActive === active;
            const matchesLevels = levels === null || config.totalLevels === levels;
            return matchesText && matchesActive && matchesLevels;
        });
        // The paginator keeps its offset when the array under it changes, so a
        // user filtering from page 2 would be shown an empty page of a result
        // that has one.
        this.tableFirst = 0;
    }

    resetFilters(): void {
        this.filters = { text: '', active: null, levels: null };
        this.applyFilters();
    }

    get hasFilters(): boolean {
        return !!(this.filters.text?.trim())
            || this.filters.active !== null
            || this.filters.levels !== null;
    }

    /**
     * The description in the language being read.
     *
     * <p>`ApprovalConfigs` carries both, and the column showed the English one
     * whatever the language was. When a row has only one of the two it is shown
     * as it is: a blank cell says less than the wrong language does, and one of
     * the twelve live workflows has no Arabic description at all.
     */
    configDescription(config: Partial<ApprovalConfig> | null | undefined): string {
        if (!config) { return '\u2014'; }
        const arabic = this.translationService.getCurrentLanguage() !== 'en';
        const primary = arabic ? config.descriptionAr : config.description;
        const fallback = arabic ? config.description : config.descriptionAr;
        return (primary?.trim() || fallback?.trim()) ?? '\u2014';
    }

    /** "Showing 1 to 10 of 12 configurations", in the reader's language. */
    get reportTemplate(): string {
        return this.translationService.translate('APPROVAL.configsReportTemplate');
    }

    loadRoles(): void {
        // Ids and names from the rules' own endpoint (live QA M6): the staff role list
        // needs staff access, which most people who open this page do not hold.
        this.httpClient.get<any>(`${environment.apiUrl}/mohe/api/v1/approval-configs/role-options`).subscribe({
            next: (roles) => {
                // Guarded for the same reason the config list is: a payload that
                // is not the array this expects would take the whole screen down
                // rather than leaving the role picker empty.
                const arabic = this.translationService.getCurrentLanguage() === 'ar';
                this.roleOptions = (Array.isArray(roles) ? roles : []).map((role: any) => ({
                    label: (arabic && role.roleNameAr) || role.roleName || role.name,
                    value: role.id
                }));
            },
            error: (error) => {
                console.error('Error loading roles:', error);
                this.messageService.add({
                    severity: 'error',
                    summary: this.translationService.translate('COMMON.ERROR'),
                    detail: this.translationService.translate('APPROVAL.failedLoadRoles')
                });
                // Fallback to empty array if API fails
                this.roleOptions = [];
            }
        });
    }

    loadConfigs(): void {
        this.loading = true;
        this.approvalService.getAllConfigs().subscribe({
            next: (data) => {
                // Normalised here rather than trusted: the table used to render
                // whatever arrived, but the filter reads it as a list, and a
                // page object where an array was expected would take the screen
                // down instead of leaving it empty.
                this.configs = Array.isArray(data) ? data : [];
                this.refreshLevelOptions();
                this.applyFilters();
                this.loading = false;
            },
            error: (err) => {
                console.error('Error loading configs:', err);
                this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('APPROVAL.failedLoadConfigs') });
                this.loading = false;
            }
        });
    }

    openNewConfigDialog(): void {
        this.configSubmitted = false;
        this.editingConfig = {
            entityType: '',
            description: '',
            totalLevels: 2,
            isActive: true
        };
        this.showConfigDialog = true;
    }

    openEditDialog(config: ApprovalConfig): void {
        this.configSubmitted = false;
        this.editingConfig = { ...config };
        this.showConfigDialog = true;
    }

    saveConfig(): void {
        this.configSubmitted = true;
        if (!this.editingConfig?.entityType?.trim()) return;

        this.saving = true;
        const isNew = !this.editingConfig.id;

        const obs = isNew
            ? this.approvalService.createConfig(this.editingConfig as ApprovalConfig)
            : this.approvalService.updateConfig(this.editingConfig.id!, this.editingConfig as ApprovalConfig);

        obs.subscribe({
            next: () => {
                this.messageService.add({
                    severity: 'success',
                    summary: this.translationService.translate('COMMON.SUCCESS'),
                    detail: this.translationService.translate('APPROVAL.configSaved')
                });
                this.showConfigDialog = false;
                this.loadConfigs();
                this.saving = false;
            },
            error: () => {
                this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('APPROVAL.failedSaveConfig') });
                this.saving = false;
            }
        });
    }

    confirmDeactivate(config: ApprovalConfig): void {
        this.confirmationService.confirm({
            message: this.translationService.translate('APPROVAL.confirmDeactivateMsg'),
            header: this.translationService.translate('APPROVAL.confirmDeactivation'),
            icon: 'pi pi-exclamation-triangle',
            accept: () => {
                this.approvalService.deactivateConfig(config.id).subscribe({
                    next: () => {
                        this.messageService.add({ severity: 'success', summary: this.translationService.translate('COMMON.SUCCESS'), detail: this.translationService.translate('APPROVAL.configDeactivated') });
                        this.loadConfigs();
                    },
                    error: () => {
                        this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('APPROVAL.failedDeactivate') });
                    }
                });
            }
        });
    }

    openLevelsDialog(config: ApprovalConfig): void {
        this.selectedConfig = config;
        this.loadLevels(config.id);
        this.showLevelsDialog = true;
    }

    loadLevels(configId: number): void {
        this.approvalService.getLevels(configId).subscribe({
            next: (data) => this.levels = data,
            error: () => this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('APPROVAL.failedLoadLevels') })
        });
    }

    openNewLevelDialog(): void {
        this.levelSubmitted = false;
        this.editingLevel = {
            levelName: '',
            requiredRoleId: null,
            requiredPositionCode: null,
            namedApprovers: [],
            canSkip: false,
            isActive: true
        };
        this.loadCandidates();
        this.showLevelDialog = true;
    }

    openEditLevelDialog(level: ApprovalLevel): void {
        this.levelSubmitted = false;
        this.editingLevel = { ...level, namedApprovers: [...(level.namedApprovers || [])] };
        this.loadCandidates();
        this.showLevelDialog = true;
    }

    saveLevel(): void {
        this.levelSubmitted = true;
        if (!this.editingLevel?.levelName?.trim() || !this.levelNamesWhoApproves()
            || !this.selectedConfig) {
            return;
        }

        this.savingLevel = true;
        const isNew = !this.editingLevel.id;

        const obs = isNew
            ? this.approvalService.addLevel(this.selectedConfig.id, this.editingLevel as ApprovalLevel)
            : this.approvalService.updateLevel(this.editingLevel.id!, this.editingLevel as ApprovalLevel);

        obs.subscribe({
            next: () => {
                this.messageService.add({ severity: 'success', summary: this.translationService.translate('COMMON.SUCCESS'), detail: this.translationService.translate('APPROVAL.levelSaved') });
                this.showLevelDialog = false;
                this.loadLevels(this.selectedConfig!.id);
                this.savingLevel = false;
            },
            error: () => {
                this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('APPROVAL.failedSaveLevel') });
                this.savingLevel = false;
            }
        });
    }

    confirmDeleteLevel(level: ApprovalLevel): void {
        this.confirmationService.confirm({
            message: this.translationService.translate('APPROVAL.deleteMsg'),
            header: this.translationService.translate('APPROVAL.confirmDeleteLevel'),
            icon: 'pi pi-trash',
            acceptButtonStyleClass: 'p-button-danger',
            accept: () => {
                this.approvalService.deleteLevel(level.id).subscribe({
                    next: () => {
                        this.messageService.add({ severity: 'success', summary: this.translationService.translate('COMMON.SUCCESS'), detail: this.translationService.translate('APPROVAL.levelDeleted') });
                        this.loadLevels(this.selectedConfig!.id);
                    },
                    error: () => this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('APPROVAL.failedDeleteLevel') })
                });
            }
        });
    }

    onLevelReorder(): void {
        if (!this.selectedConfig) return;

        const levelIds = this.levels.map(l => l.id);
        this.approvalService.reorderLevels(this.selectedConfig.id, levelIds).subscribe({
            next: () => {
                this.messageService.add({ severity: 'success', summary: this.translationService.translate('COMMON.SUCCESS'), detail: this.translationService.translate('APPROVAL.levelsReordered') });
                this.loadLevels(this.selectedConfig!.id);
            },
            error: () => this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('APPROVAL.failedReorder') })
        });
    }
}
