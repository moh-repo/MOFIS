import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { StudentQuickEditComponent } from './student-quick-edit.component';
import { StudentQuickEditRoutingModule } from './student-quick-edit-routing.module';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { RippleModule } from 'primeng/ripple';
import { TooltipModule } from 'primeng/tooltip';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { InputNumberModule } from 'primeng/inputnumber';
import { SelectModule } from 'primeng/select';
import { TabsModule } from 'primeng/tabs';
import { ToastModule } from 'primeng/toast';
import { BadgeModule } from 'primeng/badge';
import { TagModule } from 'primeng/tag';
import { DividerModule } from 'primeng/divider';
import { ScrollPanelModule } from 'primeng/scrollpanel';
import { PanelModule } from 'primeng/panel';
import { SkeletonModule } from 'primeng/skeleton';
import { ProgressBarModule } from 'primeng/progressbar';
import { SharedModule } from '../../shared/shared.module';
import { StudentDocumentsComponent } from '../../shared/student-documents/student-documents.component';

@NgModule({
    imports: [
        SkeletonModule,
        ProgressBarModule,
        CommonModule,
        FormsModule,
        StudentQuickEditRoutingModule,
        TableModule,
        ButtonModule,
        RippleModule,
        TooltipModule,
        DialogModule,
        InputTextModule,
        TextareaModule,
        InputNumberModule,
        SelectModule,
        TabsModule,
        ToastModule,
        BadgeModule,
        TagModule,
        DividerModule,
        ScrollPanelModule,
        PanelModule,
        SharedModule,
        StudentDocumentsComponent
    ],
    declarations: [StudentQuickEditComponent]
})
export class StudentQuickEditModule { }

