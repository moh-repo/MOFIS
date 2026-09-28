import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { ToastModule } from 'primeng/toast';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { RippleModule } from 'primeng/ripple';
import { ConfirmationService } from 'primeng/api';
import { SharedModule } from '../shared/shared.module';
import { HasPermissionDirective } from '../directives/has-permission.directive';
import { SettingsRoutingModule } from './settings-routing.module';
import { SettingsComponent } from './settings.component';
import { StudyYearsLookupComponent } from './study-years-lookup/study-years-lookup.component';
import { StudyAmountLookupComponent } from './study-amount-lookup/study-amount-lookup.component';
import { SystemSettingsComponent } from './settings/system-settings/system-settings.component';
import { ReportSettingsComponent } from './settings/report-settings/report-settings.component';
import { CodeListsComponent } from './settings/code-lists/code-lists.component';
import { SysParametersComponent } from './settings/sys-parameters/sys-parameters.component';
import { ToggleSwitchModule } from 'primeng/toggleswitch';

@NgModule({
  declarations: [
    SettingsComponent,
    StudyYearsLookupComponent,
    StudyAmountLookupComponent,
    SystemSettingsComponent,
    ReportSettingsComponent,
    CodeListsComponent,
    SysParametersComponent
  ],
  imports: [
    CommonModule,
    RouterModule,
    FormsModule,
    ButtonModule,
    TableModule,
    ToastModule,
    DialogModule,
    SelectModule,
    InputNumberModule,
    InputTextModule,
    TextareaModule,
    ProgressSpinnerModule,
    ConfirmDialogModule,
    TagModule,
    TooltipModule,
    RippleModule,
    ToggleSwitchModule,
    SharedModule,
    HasPermissionDirective,
    SettingsRoutingModule
  ],
  providers: [ConfirmationService]
})
export class SettingsModule { }