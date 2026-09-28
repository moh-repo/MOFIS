import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { StudentTracerComponent } from './student-tracer.component';
import { StudentTracerRoutingModule } from './student-tracer-routing.module';
import { SharedModule } from '../../shared/shared.module';

// PrimeNG Modules
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { TooltipModule } from 'primeng/tooltip';
import { ToastModule } from 'primeng/toast';
import { DividerModule } from 'primeng/divider';
import { RippleModule } from 'primeng/ripple';
import { DrawerModule } from 'primeng/drawer';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { PanelModule } from 'primeng/panel';
import { SliderModule } from 'primeng/slider';
import { SelectButtonModule } from 'primeng/selectbutton';
import { MessageModule } from 'primeng/message';

@NgModule({
    declarations: [
        StudentTracerComponent
    ],
    imports: [
        CommonModule,
        FormsModule,
        ReactiveFormsModule,
        StudentTracerRoutingModule,
        SharedModule,
        CardModule,
        ButtonModule,
        SelectModule,
        TooltipModule,
        ToastModule,
        DividerModule,
        RippleModule,
        DrawerModule,
        InputNumberModule,
        InputTextModule,
        PanelModule,
        SliderModule,
        SelectButtonModule,
        MessageModule
    ]
})
export class StudentTracerModule { }

