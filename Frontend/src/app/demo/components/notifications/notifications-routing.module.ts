import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { NotificationSettingsComponent } from './notification-settings/notification-settings.component';
import { SendNotificationComponent } from './send-notification/send-notification.component';
import { NotificationHistoryComponent } from './notification-history/notification-history.component';
import { UmsPermissionGuard } from '../../guards/ums-permission.guard';

const routes: Routes = [
    /*
     * The backend guards these endpoints with NOTIFICATIONS.SETTINGS, not
     * NOTIFICATIONS.VIEW. Each page here states its own code, and the parent
     * route none: its old NOTIFICATIONS.VIEW refused a sender or a settings
     * officer who did not also read the history. Stated here as well as on the
     * menu row, because a route is reachable by typing its URL.
     */
    {
        path: 'settings',
        component: NotificationSettingsComponent,
        canActivate: [UmsPermissionGuard],
        data: { permission: 'NOTIFICATIONS.SETTINGS' }
    },
    // NotificationController: sending (and the recipient preview) needs NOTIFICATIONS.SEND.
    {
        path: 'send',
        component: SendNotificationComponent,
        canActivate: [UmsPermissionGuard],
        data: { permission: 'NOTIFICATIONS.SEND' }
    },
    // NotificationController: the history list needs NOTIFICATIONS.VIEW.
    {
        path: 'history',
        component: NotificationHistoryComponent,
        canActivate: [UmsPermissionGuard],
        data: { permission: 'NOTIFICATIONS.VIEW' }
    },
    { path: '', redirectTo: 'send', pathMatch: 'full' }
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule]
})
export class NotificationsRoutingModule {}
