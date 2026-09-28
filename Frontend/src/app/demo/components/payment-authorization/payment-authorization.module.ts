import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PaymentAuthorizationRoutingModule } from './payment-authorization-routing.module';
import { PaymentAuthorizationComponent } from './payment-authorization.component';
import { PaymentAuthReportComponent } from './payment-auth-report/payment-auth-report.component';
// SharedModule brings <app-report-viewer> and <app-report-actions>: the statutory
// voucher and the list report are rendered by Jasper on the server and shown here,
// so the page no longer carries an HTML replica of the form.
import { SharedModule } from '../../shared/shared.module';
import { HasPermissionDirective } from '../../directives/has-permission.directive';

import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { RippleModule } from 'primeng/ripple';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { TextareaModule } from 'primeng/textarea';
import { DatePickerModule } from 'primeng/datepicker';
import { SelectModule } from 'primeng/select';
import { CheckboxModule } from 'primeng/checkbox';
import { AutoCompleteModule } from 'primeng/autocomplete';
import { DialogModule } from 'primeng/dialog';
import { ToastModule } from 'primeng/toast';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { DividerModule } from 'primeng/divider';
import { SelectButtonModule } from 'primeng/selectbutton';
import { ChipModule } from 'primeng/chip';
import { ToolbarModule } from 'primeng/toolbar';
import { ProgressSpinnerModule } from 'primeng/progressspinner';

@NgModule({
    declarations: [PaymentAuthorizationComponent, PaymentAuthReportComponent],
    imports: [
        CommonModule,
        FormsModule,
        PaymentAuthorizationRoutingModule,
        SharedModule,
        TableModule,
        ButtonModule,
        RippleModule,
        InputTextModule,
        InputNumberModule,
        TextareaModule,
        DatePickerModule,
        SelectModule,
        CheckboxModule,
        AutoCompleteModule,
        DialogModule,
        ToastModule,
        ConfirmDialogModule,
        TagModule,
        TooltipModule,
        DividerModule,
        SelectButtonModule,
        ChipModule,
        ToolbarModule,
        ProgressSpinnerModule,
        HasPermissionDirective
    ]
})
export class PaymentAuthorizationModule {}
