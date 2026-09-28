import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

import { StudentLoanRoutingModule } from './student-loan-routing.module';
import { StudentLoanComponent } from './student-loan.component';
import { StudentLoanFormComponent } from './student-loan-form.component';
import { StudentLoanViewComponent } from './student-loan-view.component';

import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { TextareaModule } from 'primeng/textarea';
import { AutoCompleteModule } from 'primeng/autocomplete';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { TooltipModule } from 'primeng/tooltip';
import { RippleModule } from 'primeng/ripple';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { PanelModule } from 'primeng/panel';

import { SharedModule } from '../../shared/shared.module';
import { HasPermissionDirective } from '../../directives/has-permission.directive';

@NgModule({
  declarations: [
    StudentLoanComponent,
    StudentLoanFormComponent,
    StudentLoanViewComponent
  ],
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    StudentLoanRoutingModule,
    TableModule,
    ButtonModule,
    DialogModule,
    SelectModule,
    InputTextModule,
    InputNumberModule,
    TextareaModule,
    AutoCompleteModule,
    TagModule,
    ToastModule,
    ConfirmDialogModule,
    TooltipModule,
    RippleModule,
    ProgressSpinnerModule,
    PanelModule,
    SharedModule,
    HasPermissionDirective
  ]
})
export class StudentLoanModule { }
