import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { StudyUniversityFeeComponent } from './studyuniversityfee.component';
import { StudyUniversityFeeRoutingModule } from './studyuniversityfee-routing.module';


import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { DatePickerModule } from 'primeng/datepicker';
import { SelectModule } from 'primeng/select';
import { InputNumberModule } from 'primeng/inputnumber';
import { TextareaModule } from 'primeng/textarea';
import { ToastModule } from 'primeng/toast';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { TagModule } from 'primeng/tag';
import { RippleModule } from 'primeng/ripple';
import { DialogModule } from 'primeng/dialog';
import { TooltipModule } from 'primeng/tooltip';
import { ToolbarModule } from 'primeng/toolbar';
import { AutoCompleteModule } from 'primeng/autocomplete';
import { TabsModule } from 'primeng/tabs';
import { SharedModule } from '../../shared/shared.module';

@NgModule({
    declarations: [StudyUniversityFeeComponent],
    imports: [
        CommonModule,
        FormsModule,
        ReactiveFormsModule,
        StudyUniversityFeeRoutingModule,
        SharedModule,
        TableModule,
        ButtonModule,
        InputTextModule,
        DatePickerModule,
        SelectModule,
        InputNumberModule,
        TextareaModule,
        ToastModule,
        ConfirmDialogModule,
        TagModule,
        RippleModule,
        DialogModule,
        TooltipModule,
        ToolbarModule,
        AutoCompleteModule,
        TabsModule
    ]
})
export class StudyUniversityFeeModule { } 