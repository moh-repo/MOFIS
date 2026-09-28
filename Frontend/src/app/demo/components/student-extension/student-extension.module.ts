import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule } from '@angular/forms';
import { StudentExtensionComponent } from './student-extension.component';
import { StudentExtensionRoutingModule } from './student-extension-routing.module';
import { SharedModule } from '../../shared/shared.module';

// PrimeNG Modules
import { CardModule } from 'primeng/card';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { InputNumberModule } from 'primeng/inputnumber';
import { SelectModule } from 'primeng/select';
import { DatePickerModule } from 'primeng/datepicker';
import { DialogModule } from 'primeng/dialog';
import { DrawerModule } from 'primeng/drawer';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ToastModule } from 'primeng/toast';
import { DividerModule } from 'primeng/divider';
import { PanelModule } from 'primeng/panel';
import { TimelineModule } from 'primeng/timeline';
import { RippleModule } from 'primeng/ripple';

@NgModule({
    declarations: [
        StudentExtensionComponent
    ],
    imports: [
        CommonModule,
        ReactiveFormsModule,
        StudentExtensionRoutingModule,
        SharedModule,
        CardModule,
        TableModule,
        ButtonModule,
        InputTextModule,
        TextareaModule,
        InputNumberModule,
        SelectModule,
        DatePickerModule,
        DialogModule,
        DrawerModule,
        TagModule,
        TooltipModule,
        ConfirmDialogModule,
        ToastModule,
        DividerModule,
        PanelModule,
        TimelineModule,
        RippleModule
    ]
})
export class StudentExtensionModule { }


