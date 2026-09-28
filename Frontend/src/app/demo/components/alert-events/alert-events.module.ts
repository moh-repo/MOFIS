import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';

import { AlertEventsRoutingModule } from './alert-events-routing.module';
import { SharedModule } from '../../shared/shared.module';
import { AlertEventsCasesComponent } from './alert-events-cases/alert-events-cases.component';
import { AlertEventsScenariosComponent } from './alert-events-scenarios/alert-events-scenarios.component';

// PrimeNG
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { PanelModule } from 'primeng/panel';
import { BadgeModule } from 'primeng/badge';
import { ToastModule } from 'primeng/toast';
import { SelectModule } from 'primeng/select';
import { InputTextModule } from 'primeng/inputtext';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { InputNumberModule } from 'primeng/inputnumber';
import { TextareaModule } from 'primeng/textarea';
import { DialogModule } from 'primeng/dialog';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { DatePickerModule } from 'primeng/datepicker';
import { DividerModule } from 'primeng/divider';
import { CardModule } from 'primeng/card';
import { SkeletonModule } from 'primeng/skeleton';
import { TooltipModule } from 'primeng/tooltip';
import { MessageService, ConfirmationService } from 'primeng/api';
import { HasPermissionDirective } from '../../directives/has-permission.directive';

@NgModule({
    declarations: [
        AlertEventsCasesComponent,
        AlertEventsScenariosComponent
    ],
    imports: [
        CommonModule,
        FormsModule,
        RouterModule,
        SharedModule,
        AlertEventsRoutingModule,
        TableModule,
        ButtonModule,
        TagModule,
        PanelModule,
        BadgeModule,
        ToastModule,
        SelectModule,
        InputTextModule,
        ToggleSwitchModule,
        InputNumberModule,
        TextareaModule,
        DialogModule,
        ConfirmDialogModule,
        DatePickerModule,
        DividerModule,
        CardModule,
        SkeletonModule,
        TooltipModule,
        HasPermissionDirective
    ],
    providers: [MessageService, ConfirmationService]
})
export class AlertEventsModule {}
