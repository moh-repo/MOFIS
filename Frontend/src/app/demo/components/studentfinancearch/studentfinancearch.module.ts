import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { StudentFinanceArchComponent } from './studentfinancearch.component';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { DatePickerModule } from 'primeng/datepicker';
import { SelectModule } from 'primeng/select';
import { InputNumberModule } from 'primeng/inputnumber';
import { DialogModule } from 'primeng/dialog';
import { TagModule } from 'primeng/tag';
import { ToolbarModule } from 'primeng/toolbar';
import { ToastModule } from 'primeng/toast';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { TooltipModule } from 'primeng/tooltip';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { StudentFinanceArchRoutingModule } from './studentfinancearch-routing.module';
import { SharedModule } from '../../shared/shared.module';
import { HasPermissionDirective } from '../../directives/has-permission.directive';

@NgModule({
    imports: [
        CommonModule,
        FormsModule,
        TableModule,
        ButtonModule,
        InputTextModule,
        TextareaModule,
        DatePickerModule,
        SelectModule,
        InputNumberModule,
        DialogModule,
        TagModule,
        ToolbarModule,
        ToastModule,
        ConfirmDialogModule,
        TooltipModule,
        ProgressSpinnerModule,
        StudentFinanceArchRoutingModule,
        SharedModule,
        HasPermissionDirective
    ],
    declarations: [StudentFinanceArchComponent],
    exports: [StudentFinanceArchComponent]
})
export class StudentFinanceArchModule { } 