import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { AccountingRoutingModule } from './accounting-routing.module';
import { ChartOfAccountsComponent } from './chart-of-accounts/chart-of-accounts.component';
import { AdvanceBalancesComponent } from './advance-balances/advance-balances.component';
import { AllocationsComponent } from './allocations/allocations.component';
import { PaymentBatchesComponent } from './payment-batches/payment-batches.component';
import { FxRatesComponent } from './fx-rates/fx-rates.component';
import { BudgetAppropriationsComponent } from './budget-appropriations/budget-appropriations.component';

import { TableModule } from 'primeng/table';
import { TreeTableModule } from 'primeng/treetable';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { TextareaModule } from 'primeng/textarea';
import { DatePickerModule } from 'primeng/datepicker';
import { CheckboxModule } from 'primeng/checkbox';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { TooltipModule } from 'primeng/tooltip';
import { RippleModule } from 'primeng/ripple';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';

import { SharedModule } from '../../shared/shared.module';
import { HasPermissionDirective } from '../../directives/has-permission.directive';

/**
 * Accounting: the chart of accounts, the attaché advances, and the fund chain
 * screens — appropriations, allocations, payment batches — plus the FX rates
 * the ledger round will translate with.
 */
@NgModule({
    declarations: [
        ChartOfAccountsComponent,
        AdvanceBalancesComponent,
        AllocationsComponent,
        PaymentBatchesComponent,
        FxRatesComponent,
        BudgetAppropriationsComponent
    ],
    imports: [
        CommonModule,
        FormsModule,
        AccountingRoutingModule,
        TableModule,
        TreeTableModule,
        ButtonModule,
        DialogModule,
        SelectModule,
        InputTextModule,
        InputNumberModule,
        TextareaModule,
        DatePickerModule,
        CheckboxModule,
        TagModule,
        ToastModule,
        ConfirmDialogModule,
        TooltipModule,
        MessageModule,
        ProgressSpinnerModule,
        RippleModule,
        SharedModule,
        HasPermissionDirective
    ]
})
export class AccountingModule {}
