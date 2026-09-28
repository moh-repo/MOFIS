import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LookupTablesRoutingModule } from './lookuptables-routing.module';
import { LookupTablesComponent } from './lookuptables.component';

// PrimeNG Modules
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { ToastModule } from 'primeng/toast';
import { RippleModule } from 'primeng/ripple';
import { DialogModule } from 'primeng/dialog';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { CheckboxModule } from 'primeng/checkbox';
import { TabsModule } from 'primeng/tabs';
import { SelectButtonModule } from 'primeng/selectbutton';
import { SharedModule } from '../../../shared/shared.module';
import { HasPermissionDirective } from '../../../directives/has-permission.directive';

@NgModule({
    declarations: [LookupTablesComponent],
    imports: [
        CommonModule,
        FormsModule,
        LookupTablesRoutingModule,
        TableModule,
        ButtonModule,
        InputTextModule,
        SelectModule,
        ToastModule,
        RippleModule,
        DialogModule,
        TagModule,
        TooltipModule,
        ConfirmDialogModule,
        CheckboxModule,
        TabsModule,
        SelectButtonModule,
        SharedModule,
        HasPermissionDirective
    ]
})
export class LookupTablesModule { }

