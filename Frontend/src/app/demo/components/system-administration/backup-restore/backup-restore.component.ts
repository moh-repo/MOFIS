import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { NzTableModule } from 'ng-zorro-antd/table';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzCardModule } from 'ng-zorro-antd/card';
import { ToastService } from '../../../service/toast.service';
import { NzModalModule, NzModalService } from 'ng-zorro-antd/modal';
import { NzUploadModule } from 'ng-zorro-antd/upload';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzSpinModule } from 'ng-zorro-antd/spin';
import { NzAlertModule } from 'ng-zorro-antd/alert';
import { NzDividerModule } from 'ng-zorro-antd/divider';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { environment } from '../../../../../environments/environment';

import { TranslationService } from '../../../service/translation.service';
import { UmsAuthService } from '../../../service/ums-auth.service';
import { TranslatePipe } from '../../../pipes/translate.pipe';

interface BackupInfo {
    filename: string;
    size: number;
    sizeFormatted: string;
    lastModified: string;
}

@Component({
    selector: 'app-backup-restore',
    imports: [
        CommonModule, NzTableModule, NzButtonModule, NzCardModule,
        NzModalModule, NzUploadModule, NzIconModule, NzSpinModule,
        NzAlertModule, NzDividerModule, NzTagModule, TranslatePipe
    ],
    template: `
        <div class="backup-container">
          <!-- The title is a template rather than a string so the card's
               heading can lead with an icon, as every other page does. -->
          <nz-card [nzTitle]="titleTemplate" [nzExtra]="extraTemplate">
            <ng-template #titleTemplate>
              <span nz-icon nzType="database" class="mx-2"></span>{{ 'BACKUP_RESTORE.TITLE' | translate }}
            </ng-template>
            <ng-template #extraTemplate>
              <button nz-button nzType="primary" (click)="createBackup()" [nzLoading]="isCreating">
                <span nz-icon nzType="database"></span>
                {{ 'BACKUP_RESTORE.CREATE_BACKUP' | translate }}
              </button>
            </ng-template>
        
            @if (lastBackupResult) {
              <nz-alert
                [nzType]="lastBackupResult.success ? 'success' : 'error'"
                [nzMessage]="lastBackupResult.message"
                nzShowIcon nzCloseable
                style="margin-bottom: 16px;">
              </nz-alert>
            }
        
            <h4>{{ 'BACKUP_RESTORE.AVAILABLE_BACKUPS' | translate }}</h4>
            <nz-table #backupTable [nzData]="backups" [nzLoading]="isLoading" nzSize="small">
              <thead>
                <tr>
                  <th>{{ 'BACKUP_RESTORE.FILENAME' | translate }}</th>
                  <th>{{ 'BACKUP_RESTORE.SIZE' | translate }}</th>
                  <th>{{ 'BACKUP_RESTORE.CREATED' | translate }}</th>
                  <th>{{ 'BACKUP_RESTORE.ACTIONS' | translate }}</th>
                </tr>
              </thead>
              <tbody>
                @for (backup of backupTable.data; track backup) {
                  <tr>
                    <td>{{ backup.filename }}</td>
                    <td><nz-tag nzColor="blue">{{ backup.sizeFormatted }}</nz-tag></td>
                    <td>{{ backup.lastModified | date:'medium' }}</td>
                    <td>
                      @if (canDownload) {
                        <button nz-button nzType="link" nzSize="small" (click)="downloadBackup(backup.filename)">
                          <span nz-icon nzType="download"></span> {{ 'BACKUP_RESTORE.DOWNLOAD' | translate }}
                        </button>
                        <nz-divider nzType="vertical"></nz-divider>
                      }
                      <button nz-button nzType="link" nzDanger nzSize="small" (click)="confirmDelete(backup.filename)">
                        <span nz-icon nzType="delete"></span> {{ 'BACKUP_RESTORE.DELETE' | translate }}
                      </button>
                    </td>
                  </tr>
                }
              </tbody>
            </nz-table>
        
            <nz-divider></nz-divider>
        
            <h4>{{ 'BACKUP_RESTORE.RESTORE_TITLE' | translate }}</h4>
            <nz-alert nzType="warning" [nzMessage]="'BACKUP_RESTORE.RESTORE_WARNING' | translate" nzShowIcon style="margin-bottom: 16px;"></nz-alert>
        
            <nz-upload
              nzAction=""
              [nzBeforeUpload]="beforeUpload"
              [nzFileList]="fileList"
              nzAccept=".bak">
              <button nz-button>
                <span nz-icon nzType="upload"></span>
                {{ 'BACKUP_RESTORE.SELECT_FILE' | translate }}
              </button>
            </nz-upload>
        
            @if (fileList.length > 0) {
              <button nz-button nzType="primary" nzDanger
                [nzLoading]="isRestoring"
                (click)="confirmRestore()"
                style="margin-top: 16px;">
                <span nz-icon nzType="reload"></span>
                {{ 'BACKUP_RESTORE.RESTORE_BTN' | translate }}
              </button>
            }
          </nz-card>
        </div>
        `,
    styles: [`
        .backup-container {
            padding: 24px;
        }
        :host ::ng-deep .ant-card-head {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            border-radius: 8px 8px 0 0;
        }
        :host ::ng-deep .ant-card-head-title {
            color: white;
            font-weight: 600;
        }
    `]
})
export class BackupRestoreComponent implements OnInit {
    backups: BackupInfo[] = [];
    isLoading = false;
    isCreating = false;
    isRestoring = false;
    fileList: any[] = [];
    lastBackupResult: { success: boolean; message: string } | null = null;

    private apiUrl = `${environment.apiUrl}/mohe/api/v1`;

    constructor(
        private http: HttpClient,
        private message: ToastService,
        private modal: NzModalService,
        private translationService: TranslationService,
        private umsAuth: UmsAuthService
    ) { }

    /** A backup is the whole database, decrypted: the super admin alone downloads one (FIX-PLAN D17-b). */
    get canDownload(): boolean {
        return this.umsAuth.isSuperAdmin();
    }

    ngOnInit(): void {
        this.loadBackups();
    }

    loadBackups(): void {
        this.isLoading = true;
        this.http.get<BackupInfo[]>(`${this.apiUrl}/backup/history`).subscribe({
            next: (data) => {
                this.backups = data;
                this.isLoading = false;
            },
            error: () => {
                this.message.error(this.translationService.translate('BACKUP_RESTORE.LOAD_FAILED'));
                this.isLoading = false;
            }
        });
    }

    createBackup(): void {
        this.isCreating = true;
        this.http.post<any>(`${this.apiUrl}/backup/create`, {}).subscribe({
            next: (result) => {
                this.lastBackupResult = { success: true, message: `${this.translationService.translate('BACKUP_RESTORE.BACKUP_SUCCESS')}: ${result.filename}` };
                this.message.success(this.translationService.translate('BACKUP_RESTORE.BACKUP_SUCCESS'));
                this.loadBackups();
                this.isCreating = false;
            },
            error: (err) => {
                this.lastBackupResult = { success: false, message: err.error?.message || this.translationService.translate('BACKUP_RESTORE.BACKUP_FAILED') };
                this.message.error(this.translationService.translate('BACKUP_RESTORE.BACKUP_FAILED'));
                this.isCreating = false;
            }
        });
    }

    downloadBackup(filename: string): void {
        // Through HttpClient, not window.open: a top-level navigation carries no
        // Authorization header - AuthInterceptor only decorates HttpClient calls -
        // and this endpoint is behind @PreAuthorize, so the old version was denied
        // every time, in a new tab where no toast could say so.
        this.http.get(`${this.apiUrl}/backup/download/${encodeURIComponent(filename)}`,
            { responseType: 'blob' }).subscribe({
            next: (blob) => {
                const url = URL.createObjectURL(blob);
                const link = document.createElement('a');
                link.href = url;
                link.download = filename;
                link.click();
                URL.revokeObjectURL(url);
            },
            error: () => this.message.error(
                this.translationService.translate('BACKUP_RESTORE.DOWNLOAD_FAILED'))
        });
    }

    confirmDelete(filename: string): void {
        this.modal.confirm({
            nzTitle: this.translationService.translate('BACKUP_RESTORE.DELETE_CONFIRM_TITLE'),
            nzContent: `${this.translationService.translate('BACKUP_RESTORE.DELETE_CONFIRM_MSG')} "${filename}"?`,
            nzOkText: this.translationService.translate('BACKUP_RESTORE.DELETE_BTN'),
            nzOkDanger: true,
            nzOnOk: () => this.deleteBackup(filename)
        });
    }

    deleteBackup(filename: string): void {
        this.http.delete(`${this.apiUrl}/backup/${filename}`).subscribe({
            next: () => {
                this.message.success(this.translationService.translate('BACKUP_RESTORE.DELETE_SUCCESS'));
                this.loadBackups();
            },
            error: () => this.message.error(this.translationService.translate('BACKUP_RESTORE.DELETE_FAILED'))
        });
    }

    beforeUpload = (file: any): boolean => {
        this.fileList = [file];
        return false; // Prevent auto-upload
    };

    confirmRestore(): void {
        this.modal.confirm({
            nzTitle: this.translationService.translate('BACKUP_RESTORE.RESTORE_CONFIRM_TITLE'),
            nzContent: this.translationService.translate('BACKUP_RESTORE.RESTORE_CONFIRM_MSG'),
            nzOkText: this.translationService.translate('BACKUP_RESTORE.RESTORE_CONFIRM_BTN'),
            nzOkDanger: true,
            nzOnOk: () => this.restoreBackup()
        });
    }

    restoreBackup(): void {
        if (this.fileList.length === 0) return;

        this.isRestoring = true;
        const formData = new FormData();
        formData.append('file', this.fileList[0]);

        this.http.post<any>(`${this.apiUrl}/backup/restore`, formData).subscribe({
            next: (res) => {
                if (res?.status === 'PENDING_APPROVAL') {
                    // S-03: a second administrator must approve; upload the same file again afterwards.
                    this.message.info(this.translationService.translate('BACKUP_RESTORE.RESTORE_PENDING_APPROVAL'));
                    this.isRestoring = false;
                    return;
                }
                this.message.success(this.translationService.translate('BACKUP_RESTORE.RESTORE_SUCCESS'));
                this.fileList = [];
                this.isRestoring = false;
            },
            error: (err) => {
                this.message.error(err.error?.message || this.translationService.translate('BACKUP_RESTORE.RESTORE_FAILED'));
                this.isRestoring = false;
            }
        });
    }
}
