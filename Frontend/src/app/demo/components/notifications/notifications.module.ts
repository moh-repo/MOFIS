import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { NotificationsRoutingModule } from './notifications-routing.module';
import { SharedModule } from '../../shared/shared.module';

// Components
import { NotificationSettingsComponent } from './notification-settings/notification-settings.component';
import { SendNotificationComponent } from './send-notification/send-notification.component';
import { NotificationHistoryComponent } from './notification-history/notification-history.component';

// PrimeNG
import { TabsModule } from 'primeng/tabs';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { InputNumberModule } from 'primeng/inputnumber';
import { AutoCompleteModule } from 'primeng/autocomplete';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { PasswordModule } from 'primeng/password';
import { DatePickerModule } from 'primeng/datepicker';
import { SelectModule } from 'primeng/select';
import { SelectButtonModule } from 'primeng/selectbutton';
import { MultiSelectModule } from 'primeng/multiselect';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { BadgeModule } from 'primeng/badge';
import { ToastModule } from 'primeng/toast';
import { DialogModule } from 'primeng/dialog';
import { EditorModule } from 'primeng/editor';
import { DividerModule } from 'primeng/divider';
import { MessageModule } from 'primeng/message';
import { MessageService, ConfirmationService } from 'primeng/api';

@NgModule({
    declarations: [
        NotificationSettingsComponent,
        SendNotificationComponent,
        NotificationHistoryComponent
    ],
    imports: [
        CommonModule,
        FormsModule,
        SharedModule,
        NotificationsRoutingModule,
        TabsModule,
        ToggleSwitchModule,
        InputNumberModule,
        AutoCompleteModule,
        InputTextModule,
        TextareaModule,
        PasswordModule,
        DatePickerModule,
        SelectModule,
        SelectButtonModule,
        MultiSelectModule,
        ButtonModule,
        TableModule,
        TagModule,
        BadgeModule,
        ToastModule,
        DialogModule,
        EditorModule,
        DividerModule,
        MessageModule
    ],
    providers: [MessageService, ConfirmationService]
})
export class NotificationsModule {}
