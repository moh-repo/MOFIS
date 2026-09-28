import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { FinanceReceivedWhomComponent } from './finance-received-whom.component';

@NgModule({
    imports: [RouterModule.forChild([
        { path: '', component: FinanceReceivedWhomComponent }
    ])],
    exports: [RouterModule]
})
export class FinanceReceivedWhomRoutingModule {}
