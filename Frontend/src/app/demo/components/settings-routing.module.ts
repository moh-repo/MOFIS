import { NgModule, inject } from '@angular/core';
import { Router, RouterModule, Routes } from '@angular/router';
import { SettingsComponent } from './settings.component';
import { StudyYearsLookupComponent } from './study-years-lookup/study-years-lookup.component';
import { StudyAmountLookupComponent } from './study-amount-lookup/study-amount-lookup.component';
import { SystemSettingsComponent } from './settings/system-settings/system-settings.component';
import { ReportSettingsComponent } from './settings/report-settings/report-settings.component';
import { CodeListsComponent } from './settings/code-lists/code-lists.component';
import { SysParametersComponent } from './settings/sys-parameters/sys-parameters.component';
import { UmsPermissionGuard } from '../guards/ums-permission.guard';

/**
 * The old country / governorate / municipality pages moved into the lookup-tables
 * screen (tab 5). A string `redirectTo` cannot carry a query string — Angular treats
 * the whole string as one path segment, so these used to land on the error page —
 * hence the function form, which returns a UrlTree with real query params.
 */
const toLookupTable = (table: string) => () =>
  inject(Router).createUrlTree(['/settings/lookuptables'], { queryParams: { tab: 5, table } });

const routes: Routes = [
  {
    path: '',
    component: SettingsComponent,
    children: [
      { path: 'lookuptables', loadChildren: () => import('./settings/lookuptables/lookuptables.module').then(m => m.LookupTablesModule) },
      { path: 'country', redirectTo: toLookupTable('country'), pathMatch: 'full' },
      { path: 'government', redirectTo: toLookupTable('governorate'), pathMatch: 'full' },
      { path: 'municipality', redirectTo: toLookupTable('municipality'), pathMatch: 'full' },
      { path: 'study-years-lookup', component: StudyYearsLookupComponent },
      { path: 'study-amount-lookup', component: StudyAmountLookupComponent },
      // Super admin only (review M2); UmsPermissionGuard reads superAdminOnly.
      { path: 'system-settings', component: SystemSettingsComponent, canActivate: [UmsPermissionGuard], data: { superAdminOnly: true } },
      { path: 'report-settings', component: ReportSettingsComponent },
      // Every code list of the system and the SYS_PARAMETER table; both under the
      // parent's SETTINGS gate, writes need SETTINGS.EDIT on the server.
      { path: 'code-lists', component: CodeListsComponent, data: { pageCode: 'SETTINGS' } },
      { path: 'sys-parameters', component: SysParametersComponent, data: { pageCode: 'SETTINGS' } },
      { path: '', redirectTo: 'lookuptables', pathMatch: 'full' }
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class SettingsRoutingModule { } 