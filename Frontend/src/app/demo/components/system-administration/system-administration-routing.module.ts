import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { UmsPermissionGuard } from '../../guards/ums-permission.guard';
import { DocumentTypesComponent } from './document-types/document-types.component';
import { StudyFinanceBankComponent } from './study-finance-bank/study-finance-bank.component';
import { StudyFinanceBankBranchComponent } from './study-finance-bank-branch/study-finance-bank-branch.component';
import { StudyWafarTypeComponent } from './study-wafar-type/study-wafar-type.component';

const routes: Routes = [
  { path: 'document-types', component: DocumentTypesComponent, data: { pageCode: 'SETTINGS' }, canActivate: [UmsPermissionGuard] },
  { path: 'study-finance-bank', component: StudyFinanceBankComponent, data: { pageCode: 'SETTINGS' }, canActivate: [UmsPermissionGuard] },
  { path: 'study-finance-bank-branch', component: StudyFinanceBankBranchComponent, data: { pageCode: 'SETTINGS' }, canActivate: [UmsPermissionGuard] },
  { path: 'study-wafar-type', component: StudyWafarTypeComponent, data: { pageCode: 'SETTINGS' }, canActivate: [UmsPermissionGuard] },
  /*
   * SETTINGS, not BACKUP/DATA_IMPORT: neither BACKUP.VIEW nor DATA_IMPORT.VIEW
   * exists in UMSCORE.Permissions, so these routes could only ever be reached by
   * the admin bypass while the APIs behind them accept SETTINGS.VIEW — the screen
   * was stricter than the endpoint. SETTINGS is what the menu already maps these
   * links to and what the controllers already check.
   */
  {
    path: 'backup-restore',
    loadComponent: () => import('./backup-restore/backup-restore.component').then(m => m.BackupRestoreComponent),
    data: { pageCode: 'SETTINGS' },
    canActivate: [UmsPermissionGuard]
  },
  {
    path: 'data-import',
    loadComponent: () => import('./data-import/data-import.component').then(m => m.DataImportComponent),
    data: { pageCode: 'SETTINGS' },
    canActivate: [UmsPermissionGuard]
  },
  { path: '', pathMatch: 'full', redirectTo: 'document-types' }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class SystemAdministrationRoutingModule { }
