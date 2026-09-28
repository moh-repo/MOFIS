import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { StudentHistoryComponent } from './student-history.component';
import { StudentHistoryRoutingModule } from './student-history-routing.module';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { InputNumberModule } from 'primeng/inputnumber';
import { SelectModule } from 'primeng/select';
import { DialogModule } from 'primeng/dialog';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { RippleModule } from 'primeng/ripple';
import { TooltipModule } from 'primeng/tooltip';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { AccordionModule } from 'primeng/accordion';
import { CardModule } from 'primeng/card';
import { SkeletonModule } from 'primeng/skeleton';
import { ProgressBarModule } from 'primeng/progressbar';
import { SharedModule } from '../../shared/shared.module';

@NgModule({
    imports: [
        SkeletonModule,
        ProgressBarModule,
        CommonModule,
        FormsModule,
        StudentHistoryRoutingModule,
        TableModule,
        ButtonModule,
        InputTextModule,
        TextareaModule,
        InputNumberModule,
        SelectModule,
        DialogModule,
        ProgressSpinnerModule,
        RippleModule,
        TooltipModule,
        TagModule,
        ToastModule,
        AccordionModule,
        CardModule,
        SharedModule
    ],
    declarations: [StudentHistoryComponent]
})
export class StudentHistoryModule { }
