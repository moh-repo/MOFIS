import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuditLogComponent } from './audit-log.component';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { RippleModule } from 'primeng/ripple';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { DatePickerModule } from 'primeng/datepicker';
import { DialogModule } from 'primeng/dialog';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { TooltipModule } from 'primeng/tooltip';
import { PanelModule } from 'primeng/panel';
import { MessageService } from 'primeng/api';
import { RouterModule } from '@angular/router';

import { SharedModule } from '../../shared/shared.module';
import { HasPermissionDirective } from '../../directives/has-permission.directive';

@NgModule({
    imports: [
        CommonModule,
        FormsModule,
        TableModule,
        ButtonModule,
        RippleModule,
        InputTextModule,
        SelectModule,
        DatePickerModule,
        DialogModule,
        TagModule,
        ToastModule,
        TooltipModule,
        PanelModule,
        SharedModule,
        HasPermissionDirective,
        RouterModule.forChild([
            { path: 'logs', component: AuditLogComponent }
        ])
    ],
    declarations: [AuditLogComponent],
    providers: [MessageService]
})
export class AuditLogModule { }
