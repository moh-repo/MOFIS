import { NgModule } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { StudentPortalRoutingModule } from './student-portal-routing.module';
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

import { NzLayoutModule } from 'ng-zorro-antd/layout';
import { NzMenuModule } from 'ng-zorro-antd/menu';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzTableModule } from 'ng-zorro-antd/table';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzModalModule } from 'ng-zorro-antd/modal';
import { NzSpinModule } from 'ng-zorro-antd/spin';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { NzDividerModule } from 'ng-zorro-antd/divider';
import { NzDescriptionsModule } from 'ng-zorro-antd/descriptions';
import { NzUploadModule } from 'ng-zorro-antd/upload';
import { NzPaginationModule } from 'ng-zorro-antd/pagination';
import { NzGridModule } from 'ng-zorro-antd/grid';
import { NzAlertModule } from 'ng-zorro-antd/alert';
import { NzTooltipModule } from 'ng-zorro-antd/tooltip';
import { NzResultModule } from 'ng-zorro-antd/result';
import { NzTypographyModule } from 'ng-zorro-antd/typography';
import { NzProgressModule } from 'ng-zorro-antd/progress';
import { NzPopconfirmModule } from 'ng-zorro-antd/popconfirm';
import { NzCheckboxModule } from 'ng-zorro-antd/checkbox';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzBadgeModule } from 'ng-zorro-antd/badge';
import { TranslatePipe } from '../demo/pipes/translate.pipe';
import { InLanguagePipe } from '../demo/pipes/in-language.pipe';
import { BidiModule } from '@angular/cdk/bidi';
import { LocaleDatePipe } from '../demo/pipes/locale-date.pipe';

@NgModule({
  providers: [DatePipe],
  declarations: [
    PortalLayoutComponent,
    StudentPortalLoginComponent,
    StudentPortalDashboardComponent,
    MyDataComponent,
    DocumentsComponent,
    InquiriesComponent,
    InfoRequestsComponent,
    TravelTicketsComponent,
    ExtensionsComponent,
    YearResultsComponent,
    FinanceStatusComponent,
    MyRequestsComponent,
    SecurityComponent,
    PortalNotFoundComponent
  ],
  imports: [
    CommonModule,
    FormsModule,
    RouterModule,
    StudentPortalRoutingModule,
    NzLayoutModule,
    NzMenuModule,
    NzIconModule,
    NzCardModule,
    NzButtonModule,
    NzTableModule,
    NzFormModule,
    NzInputModule,
    NzModalModule,

    NzSpinModule,
    NzEmptyModule,
    NzTagModule,
    NzDividerModule,
    NzDescriptionsModule,
    NzUploadModule,
    NzPaginationModule,
    NzGridModule,
    NzAlertModule,
    NzTooltipModule,
    NzResultModule,
    NzTypographyModule,
    NzProgressModule,
    NzPopconfirmModule,
    NzCheckboxModule,
    NzSelectModule,
    NzBadgeModule,
    TranslatePipe,
    InLanguagePipe,
    BidiModule,
    // The reader's calendar, digits and a date-only field without a time (live #16).
    LocaleDatePipe
  ]
})
export class StudentPortalModule {}
