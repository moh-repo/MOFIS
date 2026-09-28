import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

import { OperationsRoutingModule } from './operations-routing.module';
import { MoheQuarterComponent } from './mohe-quarter/mohe-quarter.component';

// PrimeNG
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { DatePickerModule } from 'primeng/datepicker';
import { ToastModule } from 'primeng/toast';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { TooltipModule } from 'primeng/tooltip';
import { ToolbarModule } from 'primeng/toolbar';
import { RippleModule } from 'primeng/ripple';
import { TagModule } from 'primeng/tag';
import { SelectModule } from 'primeng/select';
import { TextareaModule } from 'primeng/textarea';
import { ProgressBarModule } from 'primeng/progressbar';

import { SharedModule } from '../../shared/shared.module';
import { HasPermissionDirective } from '../../directives/has-permission.directive';

@NgModule({
  declarations: [MoheQuarterComponent],
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    OperationsRoutingModule,
    TableModule,
    ButtonModule,
    DialogModule,
    InputTextModule,
    InputNumberModule,
    DatePickerModule,
    ToastModule,
    ConfirmDialogModule,
    TooltipModule,
    ToolbarModule,
    RippleModule,
    TagModule,
    SelectModule,
    TextareaModule,
    ProgressBarModule,
    SharedModule,
    HasPermissionDirective
  ]
})
export class OperationsModule { }
