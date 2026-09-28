import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

import { SystemAdministrationRoutingModule } from './system-administration-routing.module';
import { DocumentTypesComponent } from './document-types/document-types.component';
import { StudyFinanceBankComponent } from './study-finance-bank/study-finance-bank.component';
import { StudyFinanceBankBranchComponent } from './study-finance-bank-branch/study-finance-bank-branch.component';
import { StudyWafarTypeComponent } from './study-wafar-type/study-wafar-type.component';

// PrimeNG
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { InputNumberModule } from 'primeng/inputnumber';
import { CheckboxModule } from 'primeng/checkbox';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { DatePickerModule } from 'primeng/datepicker';
import { ToastModule } from 'primeng/toast';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { TooltipModule } from 'primeng/tooltip';
import { ToolbarModule } from 'primeng/toolbar';
import { RippleModule } from 'primeng/ripple';
import { TagModule } from 'primeng/tag';
import { SelectModule } from 'primeng/select';

import { SharedModule } from '../../shared/shared.module';
import { HasPermissionDirective } from '../../directives/has-permission.directive';

@NgModule({
  declarations: [
    DocumentTypesComponent,
    StudyFinanceBankComponent,
    StudyFinanceBankBranchComponent,
    StudyWafarTypeComponent
  ],
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    SystemAdministrationRoutingModule,
    TableModule,
    ButtonModule,
    DialogModule,
    InputTextModule,
    TextareaModule,
    InputNumberModule,
    CheckboxModule,
    ToggleSwitchModule,
    DatePickerModule,
    SelectModule,
    ToastModule,
    ConfirmDialogModule,
    TooltipModule,
    ToolbarModule,
    RippleModule,
    TagModule,
    SharedModule,
    HasPermissionDirective
  ]
})
export class SystemAdministrationModule { }
