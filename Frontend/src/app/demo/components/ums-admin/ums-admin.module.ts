import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Routes } from '@angular/router';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';


// NG-ZORRO Modules
import { NzTableModule } from 'ng-zorro-antd/table';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzModalModule } from 'ng-zorro-antd/modal';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { NzSwitchModule } from 'ng-zorro-antd/switch';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzTabsModule } from 'ng-zorro-antd/tabs';
import { NzTransferModule } from 'ng-zorro-antd/transfer';
import { NzBadgeModule } from 'ng-zorro-antd/badge';

// Components
import { UserManagementComponent } from './user-management/user-management.component';
import { GroupManagementComponent } from './group-management/group-management.component';
import { RoleManagementComponent } from './role-management/role-management.component';
import { PermissionManagementComponent } from './permission-management/permission-management.component';
import { PageManagementComponent } from './page-management/page-management.component';
import { ReportManagementComponent } from './report-management/report-management.component';
import { SessionManagementComponent } from './session-management/session-management.component';
import { FieldManagementComponent } from './field-management/field-management.component';
import { ApiLinkManagementComponent } from './api-link-management/api-link-management.component';
import { UserFormComponent } from './user-form/user-form.component';
import { UserViewComponent } from './user-view/user-view.component';
import { EffectivePermissionsComponent } from './effective-permissions/effective-permissions.component';
import { AccessReviewComponent } from './access-review/access-review.component';
import { UmsPermissionGuard } from '../../guards/ums-permission.guard';
import { validIdGuard } from '../../guards/valid-id.guard';

const routes: Routes = [
    {
        path: '',
        redirectTo: 'users',
        pathMatch: 'full'
    },
    {
        path: 'users',
        component: UserManagementComponent,
        canActivate: [UmsPermissionGuard],
        data: { pageCode: 'UMS_ADMIN' }
    },
    {
        // The full add page; the list's "Basic info" dialog is the short way in.
        path: 'users/new',
        component: UserFormComponent,
        canActivate: [UmsPermissionGuard],
        data: { pageCode: 'UMS_ADMIN', permission: 'UMS_ADMIN.ADD' }
    },
    {
        // The account page: state, everything it reaches, and the actions on it.
        path: 'users/:id',
        component: UserViewComponent,
        canActivate: [validIdGuard, UmsPermissionGuard],
        data: { pageCode: 'UMS_ADMIN' }
    },
    {
        path: 'users/:id/edit',
        component: UserFormComponent,
        canActivate: [validIdGuard, UmsPermissionGuard],
        data: { pageCode: 'UMS_ADMIN', permission: 'UMS_ADMIN.EDIT' }
    },
    {
        // R-13: what an account can do and why; the R-12 cutover check per account.
        path: 'effective-permissions',
        component: EffectivePermissionsComponent,
        canActivate: [UmsPermissionGuard],
        data: { pageCode: 'UMS_ADMIN' }
    },
    {
        // S-04: the quarterly access review, attested by the attaché's own administrator.
        path: 'access-reviews',
        component: AccessReviewComponent,
        canActivate: [UmsPermissionGuard],
        data: { pageCode: 'ACCESS_REVIEW' }
    },
    {
        path: 'groups',
        component: GroupManagementComponent,
        canActivate: [UmsPermissionGuard],
        data: { pageCode: 'UMS_ADMIN' }
    },
    {
        path: 'roles',
        component: RoleManagementComponent,
        canActivate: [UmsPermissionGuard],
        data: { pageCode: 'UMS_ADMIN' }
    },
    {
        path: 'permissions',
        component: PermissionManagementComponent,
        canActivate: [UmsPermissionGuard],
        data: { pageCode: 'UMS_ADMIN' }
    },
    {
        path: 'pages',
        component: PageManagementComponent,
        canActivate: [UmsPermissionGuard],
        data: { pageCode: 'UMS_ADMIN', ministryAdminOnly: true }
    },
    {
        path: 'reports-admin',
        component: ReportManagementComponent,
        canActivate: [UmsPermissionGuard],
        data: { pageCode: 'UMS_ADMIN', ministryAdminOnly: true }
    },
    {
        path: 'sessions',
        component: SessionManagementComponent,
        canActivate: [UmsPermissionGuard],
        data: { pageCode: 'UMS_ADMIN' }
    },
    {
        path: 'fields',
        component: FieldManagementComponent,
        canActivate: [UmsPermissionGuard],
        data: { pageCode: 'UMS_ADMIN', ministryAdminOnly: true }
    },
    {
        path: 'api-links',
        component: ApiLinkManagementComponent,
        canActivate: [UmsPermissionGuard],
        data: { pageCode: 'UMS_ADMIN', ministryAdminOnly: true }
    }
];

@NgModule({
    imports: [
        CommonModule,
        FormsModule,
        ReactiveFormsModule,

        RouterModule.forChild(routes),
        NzTableModule,
        NzButtonModule,
        NzInputModule,
        NzIconModule,
        NzModalModule,
        NzFormModule,
        NzSelectModule,
        NzTagModule,
        NzSwitchModule,
        NzCardModule,
        NzTabsModule,

        NzTransferModule,
        NzBadgeModule,
        // Standalone components
        UserManagementComponent,
        UserFormComponent,
        UserViewComponent,
        GroupManagementComponent,
        RoleManagementComponent,
        PermissionManagementComponent,
        PageManagementComponent,
        ReportManagementComponent,
        SessionManagementComponent,
        FieldManagementComponent,
        ApiLinkManagementComponent
    ],
    exports: [RouterModule]
})
export class UmsAdminModule { }
