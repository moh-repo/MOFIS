import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule, Routes } from '@angular/router';

import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { DatePickerModule } from 'primeng/datepicker';
import { TextareaModule } from 'primeng/textarea';
import { RadioButtonModule } from 'primeng/radiobutton';
import { ToastModule } from 'primeng/toast';
import { StepsModule } from 'primeng/steps';
import { AutoCompleteModule } from 'primeng/autocomplete';
import { MultiSelectModule } from 'primeng/multiselect';

import { StudentInfoRequestsComponent } from './student-info-requests.component';
import { SharedModule } from '../../shared/shared.module';

const routes: Routes = [
  { path: '', component: StudentInfoRequestsComponent }
];

@NgModule({
  declarations: [StudentInfoRequestsComponent],
  imports: [
    CommonModule,
    FormsModule,
    RouterModule.forChild(routes),
    SharedModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    DialogModule,
    SelectModule,
    DatePickerModule,
    TextareaModule,
    RadioButtonModule,
    ToastModule,
    StepsModule,
    AutoCompleteModule,
    MultiSelectModule
  ]
})
export class StudentInfoRequestsModule { }
