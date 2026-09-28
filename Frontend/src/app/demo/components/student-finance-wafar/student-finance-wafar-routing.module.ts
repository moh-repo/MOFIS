import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { StudentFinanceWafarComponent } from './student-finance-wafar.component';

const routes: Routes = [
  {
    path: '',
    component: StudentFinanceWafarComponent
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class StudentFinanceWafarRoutingModule { }
