import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { ChartOfAccountsComponent } from './chart-of-accounts/chart-of-accounts.component';
import { AdvanceBalancesComponent } from './advance-balances/advance-balances.component';
import { AllocationsComponent } from './allocations/allocations.component';
import { PaymentBatchesComponent } from './payment-batches/payment-batches.component';
import { FxRatesComponent } from './fx-rates/fx-rates.component';
import { BudgetAppropriationsComponent } from './budget-appropriations/budget-appropriations.component';
import { UmsPermissionGuard } from '../../guards/ums-permission.guard';

/**
 * Every accounting read on the server is ACCOUNTING_VIEW, which accepts
 * ACCOUNTING.* or FINANCE.VIEW. Stated on each child as well as on the parent
 * route, because a route is reachable by typing its URL.
 */
const view = { canActivate: [UmsPermissionGuard], data: { pageCodes: ['ACCOUNTING', 'FINANCE'] } };

const routes: Routes = [
    { path: '', redirectTo: 'chart-of-accounts', pathMatch: 'full' },
    { path: 'chart-of-accounts', component: ChartOfAccountsComponent, ...view },
    { path: 'advance-balances', component: AdvanceBalancesComponent, ...view },
    { path: 'budget-appropriations', component: BudgetAppropriationsComponent, ...view },
    { path: 'allocations', component: AllocationsComponent, ...view },
    { path: 'payment-batches', component: PaymentBatchesComponent, ...view },
    { path: 'fx-rates', component: FxRatesComponent, ...view }
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule]
})
export class AccountingRoutingModule {}
