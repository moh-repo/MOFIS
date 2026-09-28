import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { StudentPortalGuard } from './student-portal.guard';
import { PortalLayoutComponent } from './portal-layout/portal-layout.component';
import { StudentPortalLoginComponent } from './login/login.component';
import { StudentPortalDashboardComponent } from './dashboard/dashboard.component';
import { MyDataComponent } from './my-data/my-data.component';
import { DocumentsComponent } from './documents/documents.component';
import { InquiriesComponent } from './inquiries/inquiries.component';
import { InfoRequestsComponent } from './info-requests/info-requests.component';
import { TravelTicketsComponent } from './travel-tickets/travel-tickets.component';
import { ExtensionsComponent } from './extensions/extensions.component';
import { YearResultsComponent } from './year-results/year-results.component';
import { FinanceStatusComponent } from './finance-status/finance-status.component';
import { MyRequestsComponent } from './my-requests/my-requests.component';
import { PortalNotFoundComponent } from './portal-not-found/portal-not-found.component';
import { SecurityComponent } from './security/security.component';

const routes: Routes = [
  { path: 'login', component: StudentPortalLoginComponent },
  {
    path: '',
    component: PortalLayoutComponent,
    canActivate: [StudentPortalGuard],
    children: [
      { path: 'dashboard', component: StudentPortalDashboardComponent },
      { path: 'my-data', component: MyDataComponent },
      { path: 'documents', component: DocumentsComponent },
      { path: 'inquiries', component: InquiriesComponent },
      { path: 'info-requests', component: InfoRequestsComponent },
      { path: 'travel-tickets', component: TravelTicketsComponent },
      { path: 'extensions', component: ExtensionsComponent },
      { path: 'year-results', component: YearResultsComponent },
      { path: 'finance-status', component: FinanceStatusComponent },
      { path: 'my-requests', component: MyRequestsComponent },
      { path: 'security', component: SecurityComponent },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      /*
       * An unknown portal URL is said to be unknown, inside the layout. It used
       * to redirect to the dashboard, which hid the mistake. Being a child of the
       * guarded layout, a visitor without a portal token is sent to login first.
       */
      { path: '**', component: PortalNotFoundComponent }
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class StudentPortalRoutingModule {}
