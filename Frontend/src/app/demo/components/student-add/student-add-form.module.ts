import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { StudentAddComponent } from './student-add.component';
import { SharedModule } from '../../shared/shared.module';

import { StepsModule } from 'primeng/steps';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { InputNumberModule } from 'primeng/inputnumber';
import { DatePickerModule } from 'primeng/datepicker';
import { ToastModule } from 'primeng/toast';
import { TextareaModule } from 'primeng/textarea';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { AutoCompleteModule } from 'primeng/autocomplete';
import { RippleModule } from 'primeng/ripple';
import { CardModule } from 'primeng/card';
import { FileUploadModule } from 'primeng/fileupload';
import { TableModule } from 'primeng/table';
import { TooltipModule } from 'primeng/tooltip';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ConfirmationService } from 'primeng/api';
import { PhotoCropperComponent } from '../../shared/photo-cropper/photo-cropper.component';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { DialogModule } from 'primeng/dialog';
import { TagModule } from 'primeng/tag';

/**
 * Embed the full student wizard (same as /student-add) inside other features, e.g. a modal on the students list.
 * Routed {@link StudentAddModule} imports this module plus routing.
 */
@NgModule({
    imports: [
        PhotoCropperComponent,
        CommonModule,
        FormsModule,
        StepsModule,
        ButtonModule,
        InputTextModule,
        SelectModule,
        InputNumberModule,
        DatePickerModule,
        ToastModule,
        TextareaModule,
        ToggleSwitchModule,
        AutoCompleteModule,
        RippleModule,
        CardModule,
        FileUploadModule,
        TableModule,
        TooltipModule,
        ConfirmDialogModule,
        ProgressSpinnerModule,
        DialogModule,
        TagModule,
        SharedModule
    ],
    declarations: [StudentAddComponent],
    exports: [StudentAddComponent],
    providers: [ConfirmationService]
})
export class StudentAddFormModule { }
