import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { WafarRoutingModule } from './wafar-routing.module';
import { WafarComponent } from './wafar.component';

import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { DatePickerModule } from 'primeng/datepicker';
import { TextareaModule } from 'primeng/textarea';
import { TagModule } from 'primeng/tag';
import { PanelModule } from 'primeng/panel';
import { ToastModule } from 'primeng/toast';
import { TooltipModule } from 'primeng/tooltip';
import { RippleModule } from 'primeng/ripple';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { AutoCompleteModule } from 'primeng/autocomplete';
import { ConfirmDialogModule } from 'primeng/confirmdialog';

import { SharedModule } from '../../shared/shared.module';
import { HasPermissionDirective } from '../../directives/has-permission.directive';

@NgModule({
    declarations: [WafarComponent],
    imports: [
        CommonModule,
        FormsModule,
        WafarRoutingModule,
        TableModule,
        ButtonModule,
        DialogModule,
        SelectModule,
        InputTextModule,
        InputNumberModule,
        DatePickerModule,
        TextareaModule,
        TagModule,
        PanelModule,
        ToastModule,
        TooltipModule,
        ProgressSpinnerModule,
        AutoCompleteModule,
        ConfirmDialogModule,
        RippleModule,
        SharedModule,
        HasPermissionDirective
    ]
})
export class WafarModule {}
