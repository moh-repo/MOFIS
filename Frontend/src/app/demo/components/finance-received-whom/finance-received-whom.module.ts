import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FinanceReceivedWhomRoutingModule } from './finance-received-whom-routing.module';
import { FinanceReceivedWhomComponent } from './finance-received-whom.component';

import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { DatePickerModule } from 'primeng/datepicker';
import { SelectModule } from 'primeng/select';
import { ToastModule } from 'primeng/toast';
import { RippleModule } from 'primeng/ripple';
import { DialogModule } from 'primeng/dialog';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ToolbarModule } from 'primeng/toolbar';
import { TextareaModule } from 'primeng/textarea';
import { DividerModule } from 'primeng/divider';
import { AutoCompleteModule } from 'primeng/autocomplete';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { SharedModule } from '../../shared/shared.module';

@NgModule({
    declarations: [FinanceReceivedWhomComponent],
    imports: [
        CommonModule,
        FormsModule,
        FinanceReceivedWhomRoutingModule,
        TableModule,
        ButtonModule,
        InputTextModule,
        InputNumberModule,
        DatePickerModule,
        SelectModule,
        ToastModule,
        RippleModule,
        DialogModule,
        TagModule,
        TooltipModule,
        ConfirmDialogModule,
        ToolbarModule,
        TextareaModule,
        DividerModule,
        AutoCompleteModule,
        ProgressSpinnerModule,
        SharedModule
    ]
})
export class FinanceReceivedWhomModule {}
