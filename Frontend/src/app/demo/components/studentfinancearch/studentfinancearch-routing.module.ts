import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { StudentFinanceArchComponent } from './studentfinancearch.component';

const routes: Routes = [
    {
        path: '',
        component: StudentFinanceArchComponent
    }
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule]
})
export class StudentFinanceArchRoutingModule { } 