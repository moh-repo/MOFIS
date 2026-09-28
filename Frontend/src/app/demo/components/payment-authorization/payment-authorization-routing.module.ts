import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { PaymentAuthorizationComponent } from './payment-authorization.component';
import { PaymentAuthReportComponent } from './payment-auth-report/payment-auth-report.component';

const routes: Routes = [
    { path: '', component: PaymentAuthorizationComponent },
    { path: 'report/:id', component: PaymentAuthReportComponent }
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule]
})
export class PaymentAuthorizationRoutingModule {}
