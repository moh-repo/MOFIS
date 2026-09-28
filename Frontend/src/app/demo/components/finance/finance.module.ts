import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { FinanceRoutingModule } from './finance-routing.module';
import { FinanceComponent } from './finance.component';
import { FinanceFormComponent } from './finance-form/finance-form.component';
import { FinanceViewComponent } from './finance-view/finance-view.component';

// PrimeNG Modules
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
import { AutoCompleteModule } from 'primeng/autocomplete';
import { CheckboxModule } from 'primeng/checkbox';
import { TextareaModule } from 'primeng/textarea';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { SharedModule } from '../../shared/shared.module';
import { HasPermissionDirective } from '../../directives/has-permission.directive';
import { ApprovalChangesComponent } from '../../shared/approval-changes/approval-changes.component';

@NgModule({
  declarations: [FinanceComponent, FinanceFormComponent, FinanceViewComponent],
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    FinanceRoutingModule,
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
    AutoCompleteModule,
    CheckboxModule,
    TextareaModule,
    ProgressSpinnerModule,
    SharedModule,
    HasPermissionDirective,
    ApprovalChangesComponent
  ]
})
export class FinanceModule { }
