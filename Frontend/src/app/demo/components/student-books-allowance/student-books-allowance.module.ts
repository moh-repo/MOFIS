import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

import { StudentBooksAllowanceRoutingModule } from './student-books-allowance-routing.module';
import { StudentBooksAllowanceComponent } from './student-books-allowance.component';
import { StudentBooksAllowanceFormComponent } from './student-books-allowance-form.component';
import { StudentBooksAllowanceViewComponent } from './student-books-allowance-view.component';

import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { TextareaModule } from 'primeng/textarea';
import { AutoCompleteModule } from 'primeng/autocomplete';
import { DatePickerModule } from 'primeng/datepicker';
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
    StudentBooksAllowanceComponent,
    StudentBooksAllowanceFormComponent,
    StudentBooksAllowanceViewComponent
  ],
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    StudentBooksAllowanceRoutingModule,
    TableModule,
    ButtonModule,
    DialogModule,
    SelectModule,
    InputTextModule,
    InputNumberModule,
    TextareaModule,
    AutoCompleteModule,
    DatePickerModule,
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
export class StudentBooksAllowanceModule { }
