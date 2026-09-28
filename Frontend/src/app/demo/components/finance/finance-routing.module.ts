import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { UmsPermissionGuard } from '../../guards/ums-permission.guard';
import { FinanceComponent } from './finance.component';
import { FinanceFormComponent } from './finance-form/finance-form.component';
import { FinanceViewComponent } from './finance-view/finance-view.component';
import { validIdGuard } from '../../guards/valid-id.guard';

@NgModule({
    imports: [RouterModule.forChild([
        { path: '', component: FinanceComponent },
        {
            path: 'add',
            component: FinanceFormComponent,
            canActivate: [UmsPermissionGuard],
            data: { pageCode: 'FINANCE', permission: 'FINANCE.EDIT' }
        },
        {
            path: 'edit/:id',
            component: FinanceFormComponent,
            canActivate: [validIdGuard, UmsPermissionGuard],
            data: { pageCode: 'FINANCE', permission: 'FINANCE.EDIT' }
        },
        {
            path: 'view/:id',
            component: FinanceViewComponent,
            canActivate: [validIdGuard, UmsPermissionGuard],
            data: { pageCode: 'FINANCE', permission: 'FINANCE.VIEW' }
        }
    ])],
    exports: [RouterModule]
})
export class FinanceRoutingModule { }
