import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

import { StudentFinanceWafarRoutingModule } from './student-finance-wafar-routing.module';
import { StudentFinanceWafarComponent } from './student-finance-wafar.component';

// PrimeNG modules
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { DatePickerModule } from 'primeng/datepicker';
import { TextareaModule } from 'primeng/textarea';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { TooltipModule } from 'primeng/tooltip';
import { RippleModule } from 'primeng/ripple';
import { PanelModule } from 'primeng/panel';

import { SharedModule } from '../../shared/shared.module';
import { HasPermissionDirective } from '../../directives/has-permission.directive';

@NgModule({
  declarations: [StudentFinanceWafarComponent],
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    StudentFinanceWafarRoutingModule,
    TableModule,
    ButtonModule,
    DialogModule,
    SelectModule,
    InputTextModule,
    InputNumberModule,
    DatePickerModule,
    TextareaModule,
    TagModule,
    ToastModule,
    ConfirmDialogModule,
    TooltipModule,
    RippleModule,
    PanelModule,
    SharedModule,
    HasPermissionDirective
  ]
})
export class StudentFinanceWafarModule { }
