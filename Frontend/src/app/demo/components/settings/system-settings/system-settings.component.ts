import { Component, OnInit } from '@angular/core';
import { MessageService } from 'primeng/api';
import { TranslationService } from '../../../service/translation.service';
import { SystemSettingsService, SysSettingsDto } from '../../../service/system-settings.service';
import { FaviconService } from '../../../service/favicon.service';

@Component({
    selector: 'app-system-settings',
    templateUrl: './system-settings.component.html',
    styleUrls: ['./system-settings.component.scss'],
    standalone: false,
    // Own MessageService so the local <p-toast> renders each message once instead of
    // alongside the root toast.
    providers: [MessageService]
})
export class SystemSettingsComponent implements OnInit {

    settings: SysSettingsDto = { sysNameAr: null, sysNameEn: null, sysLogoPath: null, sysLogoFilename: null };
    logoPreviewUrl: string | null = null;
    selectedFile: File | null = null;
    saving = false;
    uploading = false;

    constructor(
        private service: SystemSettingsService,
        private faviconService: FaviconService,
        private messageService: MessageService,
        public translationService: TranslationService
    ) {}

    ngOnInit(): void {
        this.loadSettings();
    }

    private loadSettings(): void {
        this.service.getSettings().subscribe({
            next: dto => {
                this.settings = dto;
                this.logoPreviewUrl = dto.sysLogoPath
                    ? this.service.getLogoUrl() + '?v=' + encodeURIComponent(dto.sysLogoFilename || '1')
                    : null;
            },
            error: () => this.showError('SYSTEM_SETTINGS.LOAD_ERROR')
        });
    }

    onLogoError(event: Event): void {
        (event.target as HTMLImageElement).style.display = 'none';
        this.logoPreviewUrl = null;
    }

    onFileSelected(event: Event): void {
        const input = event.target as HTMLInputElement;
        if (!input.files || input.files.length === 0) return;
        const file = input.files[0];
        this.selectedFile = file;
        // Local preview before upload
        const reader = new FileReader();
        reader.onload = (e) => { this.logoPreviewUrl = e.target?.result as string; };
        reader.readAsDataURL(file);
    }

    uploadLogo(): void {
        if (!this.selectedFile) return;
        this.uploading = true;
        this.service.uploadLogo(this.selectedFile).subscribe({
            next: (dto) => {
                this.settings = dto;
                this.logoPreviewUrl = this.service.getLogoUrl() + '?t=' + Date.now();
                this.selectedFile = null;
                this.uploading = false;
                // The new logo is the tab icon as well; pick it up now rather
                // than leaving the old one there until the next page load.
                this.faviconService.sync();
                this.showSuccess('SYSTEM_SETTINGS.LOGO_UPLOADED');
            },
            error: () => {
                this.uploading = false;
                this.showError('SYSTEM_SETTINGS.UPLOAD_ERROR');
            }
        });
    }

    saveNames(): void {
        this.saving = true;
        this.service.updateSettings(this.settings).subscribe({
            next: (dto) => {
                this.settings = dto;
                this.saving = false;
                this.showSuccess('COMMON.SAVED_SUCCESSFULLY');
            },
            error: () => {
                this.saving = false;
                this.showError('COMMON.SAVE_ERROR');
            }
        });
    }

    private showSuccess(key: string): void {
        this.messageService.add({ severity: 'success', summary: this.t('COMMON.SUCCESS'), detail: this.t(key) });
    }

    private showError(key: string): void {
        this.messageService.add({ severity: 'error', summary: this.t('COMMON.ERROR'), detail: this.t(key) });
    }

    t(key: string): string {
        return this.translationService.translate(key) || key;
    }

    get isRtl(): boolean {
        return this.translationService.isRTL();
    }
}
