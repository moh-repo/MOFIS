import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { MoheQuarterComponent } from './mohe-quarter/mohe-quarter.component';

const routes: Routes = [
  { path: 'mohe-quarter', component: MoheQuarterComponent },
  { path: '', pathMatch: 'full', redirectTo: 'mohe-quarter' }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class OperationsRoutingModule { }
