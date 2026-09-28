import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';

// PrimeNG Modules
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { SelectModule } from 'primeng/select';
import { DatePickerModule } from 'primeng/datepicker';
import { InputNumberModule } from 'primeng/inputnumber';
import { CardModule } from 'primeng/card';
import { ToastModule } from 'primeng/toast';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { DividerModule } from 'primeng/divider';
import { TooltipModule } from 'primeng/tooltip';
import { DrawerModule } from 'primeng/drawer';
import { TagModule } from 'primeng/tag';
import { PanelModule } from 'primeng/panel';

import { StudentTravelTicketsComponent } from './student-travel-tickets.component';
import { StudentTravelTicketsRoutingModule } from './student-travel-tickets-routing.module';
import { SharedModule } from '../../shared/shared.module';

@NgModule({
    declarations: [
        StudentTravelTicketsComponent
    ],
    imports: [
        CommonModule,
        FormsModule,
        ReactiveFormsModule,
        RouterModule,
        SharedModule,
        TableModule,
        ButtonModule,
        DialogModule,
        InputTextModule,
        TextareaModule,
        SelectModule,
        DatePickerModule,
        InputNumberModule,
        CardModule,
        ToastModule,
        ConfirmDialogModule,
        DividerModule,
        TooltipModule,
        DrawerModule,
        TagModule,
        PanelModule,
        StudentTravelTicketsRoutingModule
    ]
})
export class StudentTravelTicketsModule { }

