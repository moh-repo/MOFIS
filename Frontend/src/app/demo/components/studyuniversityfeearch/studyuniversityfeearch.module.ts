import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';

import { StudyUniversityFeeArchComponent } from './studyuniversityfeearch.component';

// PrimeNG Modules
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { DatePickerModule } from 'primeng/datepicker';
import { DialogModule } from 'primeng/dialog';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ToastModule } from 'primeng/toast';
import { ToolbarModule } from 'primeng/toolbar';
import { TagModule } from 'primeng/tag';
import { InputNumberModule } from 'primeng/inputnumber';
import { TextareaModule } from 'primeng/textarea';
import { TooltipModule } from 'primeng/tooltip';
import { SharedModule } from '../../shared/shared.module';
import { HasPermissionDirective } from '../../directives/has-permission.directive';

// PrimeFlex

const routes = [
    { path: '', component: StudyUniversityFeeArchComponent }
];

@NgModule({
    declarations: [
        StudyUniversityFeeArchComponent
    ],
    imports: [
        CommonModule,
        FormsModule,
        RouterModule.forChild(routes),
        SharedModule,
        // Add, Edit and Delete need FINANCE.EDIT on the server (live QA #8).
        HasPermissionDirective,
        
        // PrimeNG Modules
        TableModule,
        ButtonModule,
        InputTextModule,
        SelectModule,
        DatePickerModule,
        DialogModule,
        ConfirmDialogModule,
        ToastModule,
        ToolbarModule,
        TagModule,
        InputNumberModule,
        TextareaModule,
        TooltipModule
    ],
    providers: []
})
export class StudyUniversityFeeArchModule { }
