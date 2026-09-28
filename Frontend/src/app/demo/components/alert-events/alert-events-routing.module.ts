import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AlertEventsCasesComponent } from './alert-events-cases/alert-events-cases.component';
import { AlertEventsScenariosComponent } from './alert-events-scenarios/alert-events-scenarios.component';
import { UmsPermissionGuard } from '../../guards/ums-permission.guard';

const routes: Routes = [
    { path: 'cases', component: AlertEventsCasesComponent },
    /*
     * Reading the scenario list is ALERT_EVENTS_VIEW on the server; only the
     * writes (toggle, threshold, add, edit, delete) need ALERT_SCENARIOS.MANAGE,
     * and the template hides those behind *hasPermission. So the page itself
     * is gated on ALERT_EVENTS, the same as the cases page.
     */
    {
        path: 'scenarios',
        component: AlertEventsScenariosComponent,
        canActivate: [UmsPermissionGuard],
        data: { pageCode: 'ALERT_EVENTS' }
    },
    { path: '', redirectTo: 'cases', pathMatch: 'full' }
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule]
})
export class AlertEventsRoutingModule {}
