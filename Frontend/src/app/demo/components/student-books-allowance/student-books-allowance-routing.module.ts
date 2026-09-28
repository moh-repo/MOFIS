import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { StudentBooksAllowanceComponent } from './student-books-allowance.component';
import { StudentBooksAllowanceFormComponent } from './student-books-allowance-form.component';
import { StudentBooksAllowanceViewComponent } from './student-books-allowance-view.component';
import { validIdGuard } from '../../guards/valid-id.guard';

const routes: Routes = [
  { path: '', component: StudentBooksAllowanceComponent },
  { path: 'add', component: StudentBooksAllowanceFormComponent },
  { path: 'edit/:id', component: StudentBooksAllowanceFormComponent, canActivate: [validIdGuard] },
  { path: 'view/:id', component: StudentBooksAllowanceViewComponent, canActivate: [validIdGuard] }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class StudentBooksAllowanceRoutingModule { }
