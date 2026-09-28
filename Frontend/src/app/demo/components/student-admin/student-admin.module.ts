import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Routes } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';

import { StudentUsersComponent } from './student-users/student-users.component';
import { StudentRolesComponent } from './student-roles/student-roles.component';
import { StudentGroupsComponent } from './student-groups/student-groups.component';
import { StudentPermissionsComponent } from './student-permissions/student-permissions.component';
import { StudentSessionsComponent } from './student-sessions/student-sessions.component';

import { UmsPermissionGuard } from '../../guards/ums-permission.guard';

import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { DialogModule } from 'primeng/dialog';
import { ToastModule } from 'primeng/toast';
import { TooltipModule } from 'primeng/tooltip';
import { RippleModule } from 'primeng/ripple';
import { TextareaModule } from 'primeng/textarea';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ConfirmationService } from 'primeng/api';
import { InputNumberModule } from 'primeng/inputnumber';
import { ToggleSwitchModule } from 'primeng/toggleswitch';

import { TranslatePipe } from '../../pipes/translate.pipe';

const routes: Routes = [
    { path: '', redirectTo: 'users', pathMatch: 'full' },
    { path: 'users',       component: StudentUsersComponent,      canActivate: [UmsPermissionGuard], data: { pageCode: 'STUDENTS' } },
    { path: 'roles',       component: StudentRolesComponent,      canActivate: [UmsPermissionGuard], data: { pageCode: 'STUDENTS' } },
    { path: 'groups',      component: StudentGroupsComponent,     canActivate: [UmsPermissionGuard], data: { pageCode: 'STUDENTS' } },
    { path: 'permissions', component: StudentPermissionsComponent, canActivate: [UmsPermissionGuard], data: { pageCode: 'STUDENTS' } },
    { path: 'sessions',   component: StudentSessionsComponent,   canActivate: [UmsPermissionGuard], data: { pageCode: 'STUDENTS' } }
];

@NgModule({ declarations: [
        StudentUsersComponent,
        StudentRolesComponent,
        StudentGroupsComponent,
        StudentPermissionsComponent,
        StudentSessionsComponent
    ],
    exports: [RouterModule], imports: [CommonModule,
        FormsModule,
        RouterModule.forChild(routes),
        TableModule,
        ButtonModule,
        InputTextModule,
        SelectModule,
        TagModule,
        DialogModule,
        ToastModule,
        TooltipModule,
        RippleModule,
        TextareaModule,
        ConfirmDialogModule,
        InputNumberModule,
        ToggleSwitchModule,
        TranslatePipe], providers: [ConfirmationService, provideHttpClient(withInterceptorsFromDi())] })
export class StudentAdminModule {}
