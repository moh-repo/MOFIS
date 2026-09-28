import { SelectButtonModule } from 'primeng/selectbutton';
import { NgModule } from '@angular/core';
import { ApprovalChangesComponent } from '../../shared/approval-changes/approval-changes.component';
import { RouterModule, Routes } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { DialogModule } from 'primeng/dialog';
import { TextareaModule } from 'primeng/textarea';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { CardModule } from 'primeng/card';
import { SelectModule } from 'primeng/select';
import { ToastModule } from 'primeng/toast';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { CheckboxModule } from 'primeng/checkbox';
import { OrderListModule } from 'primeng/orderlist';
import { TabsModule } from 'primeng/tabs';

import { SharedModule } from '../../shared/shared.module';
import { UmsPermissionGuard } from '../../guards/ums-permission.guard';
import { PendingApprovalsComponent } from './pending-approvals/pending-approvals.component';
import { MyRequestsComponent } from './my-requests/my-requests.component';
import { ApprovalConfigsComponent } from './approval-configs/approval-configs.component';
import { ApprovalHistoryComponent } from './approval-history/approval-history.component';

/**
 * Who raises approval requests (round-5 QA R5-16, decision D-4): student, finance and
 * Wafar staff as well as approvers. My requests lists only the caller's own requests
 * (the server's /approvals/my-requests takes any signed-in account), so a Finance
 * Officer can see and withdraw what they raised without the approvals permission.
 */
export const MY_REQUESTS_PAGE_CODES = ['APPROVALS', 'STUDENTS', 'FINANCE', 'WAFAR'];

const routes: Routes = [
    { path: 'pending', component: PendingApprovalsComponent, canActivate: [UmsPermissionGuard], data: { pageCode: 'APPROVALS' } },
    { path: 'history', component: ApprovalHistoryComponent, canActivate: [UmsPermissionGuard], data: { pageCode: 'APPROVALS' } },
    { path: 'my-requests', component: MyRequestsComponent, canActivate: [UmsPermissionGuard], data: { pageCodes: MY_REQUESTS_PAGE_CODES } },
    /*
     * ApprovalConfigController is class-level @PreAuthorize(APPROVALS) — there is
     * no APPROVALS.CONFIGURE code on the server — so the configuration page is
     * gated on the same APPROVALS page code as its siblings. Each page carries its
     * own gate since R5-16 moved it off the parent.
     */
    {
        path: 'configs',
        component: ApprovalConfigsComponent,
        canActivate: [UmsPermissionGuard],
        data: { pageCode: 'APPROVALS' }
    }
];

@NgModule({
    declarations: [
        PendingApprovalsComponent,
        ApprovalHistoryComponent,
        MyRequestsComponent,
        ApprovalConfigsComponent
    ],
    imports: [
        SelectButtonModule,
        CommonModule,
        ApprovalChangesComponent,
        FormsModule,
        RouterModule.forChild(routes),
        SharedModule,
        TableModule,
        ButtonModule,
        TagModule,
        TooltipModule,
        DialogModule,
        TextareaModule,
        InputTextModule,
        InputNumberModule,
        ProgressSpinnerModule,
        CardModule,
        SelectModule,
        ToastModule,
        ConfirmDialogModule,
        CheckboxModule,
        OrderListModule,
        TabsModule
    ]
})
export class ApprovalsModule { }
