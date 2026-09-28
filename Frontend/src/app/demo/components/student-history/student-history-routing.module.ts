import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { StudentHistoryComponent } from './student-history.component';

const routes: Routes = [
    { path: '', component: StudentHistoryComponent }
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule]
})
export class StudentHistoryRoutingModule { }
