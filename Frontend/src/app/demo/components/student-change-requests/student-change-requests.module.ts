import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';

import { StudentChangeRequestsComponent } from './student-change-requests.component';

import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { DatePickerModule } from 'primeng/datepicker';
import { ToastModule } from 'primeng/toast';
import { TooltipModule } from 'primeng/tooltip';
import { RippleModule } from 'primeng/ripple';

import { TranslatePipe } from '../../pipes/translate.pipe';
import { CodeSeverityPipe } from '../../pipes/code-pipes';

@NgModule({ declarations: [StudentChangeRequestsComponent], imports: [CommonModule,
        FormsModule,
        RouterModule.forChild([{ path: '', component: StudentChangeRequestsComponent }]),
        TableModule,
        ButtonModule,
        SelectModule,
        TagModule,
        DialogModule,
        InputTextModule,
        TextareaModule,
        DatePickerModule,
        ToastModule,
        TooltipModule,
        RippleModule,
        TranslatePipe,
        CodeSeverityPipe], providers: [provideHttpClient(withInterceptorsFromDi())] })
export class StudentChangeRequestsModule {}
