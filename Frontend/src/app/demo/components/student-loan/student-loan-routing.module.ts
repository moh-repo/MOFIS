import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { StudentLoanComponent } from './student-loan.component';
import { StudentLoanFormComponent } from './student-loan-form.component';
import { StudentLoanViewComponent } from './student-loan-view.component';
import { validIdGuard } from '../../guards/valid-id.guard';

const routes: Routes = [
  { path: '', component: StudentLoanComponent },
  { path: 'add', component: StudentLoanFormComponent },
  { path: 'edit/:id', component: StudentLoanFormComponent, canActivate: [validIdGuard] },
  { path: 'view/:id', component: StudentLoanViewComponent, canActivate: [validIdGuard] }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class StudentLoanRoutingModule { }
