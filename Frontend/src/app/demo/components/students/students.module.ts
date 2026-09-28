import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { StudentsRoutingModule } from './students-routing.module';
import { StudentsComponent } from './students.component';
import { StudentProfileComponent } from './student-profile.component';
import { SharedModule } from '../../shared/shared.module';
import { StudentAddFormModule } from '../student-add/student-add-form.module';

// PrimeNG Imports
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { DialogModule } from 'primeng/dialog';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { SelectModule } from 'primeng/select';
import { DatePickerModule } from 'primeng/datepicker';
import { ToastModule } from 'primeng/toast';
import { ToolbarModule } from 'primeng/toolbar';
import { RatingModule } from 'primeng/rating';
import { InputNumberModule } from 'primeng/inputnumber';
import { RippleModule } from 'primeng/ripple';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { MenuModule } from 'primeng/menu';
import { AccordionModule } from 'primeng/accordion';
import { TabsModule } from 'primeng/tabs';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { ChipModule } from 'primeng/chip';
import { CardModule } from 'primeng/card';
import { DividerModule } from 'primeng/divider';
import { ProgressBarModule } from 'primeng/progressbar';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { AutoCompleteModule } from 'primeng/autocomplete';
import { HasPermissionDirective } from '../../directives/has-permission.directive';
import { ApprovalChangesComponent } from '../../shared/approval-changes/approval-changes.component';
import { StudentDocumentHistoryComponent } from '../../shared/student-document-history/student-document-history.component';
import { StudentDocumentsComponent } from '../../shared/student-documents/student-documents.component';

@NgModule({
    imports: [
        CommonModule,
        StudentsRoutingModule,
        FormsModule,
        TableModule,
        ButtonModule,
        InputTextModule,
        TextareaModule,
        DialogModule,
        ConfirmDialogModule,
        SelectModule,
        DatePickerModule,
        ToastModule,
        ToolbarModule,
        RatingModule,
        InputNumberModule,
        RippleModule,
        TagModule,
        TooltipModule,
        MenuModule,
        AccordionModule,
        TabsModule,
        ToggleSwitchModule,
        ChipModule,
        CardModule,
        DividerModule,
        ProgressBarModule,
        ProgressSpinnerModule,
        AutoCompleteModule,
        SharedModule,
        StudentAddFormModule,
        HasPermissionDirective,
        ApprovalChangesComponent,
        StudentDocumentHistoryComponent,
        StudentDocumentsComponent
    ],
    declarations: [StudentsComponent, StudentProfileComponent]
})
export class StudentsModule { }
