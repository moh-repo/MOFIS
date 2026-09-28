import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { StudentTravelTicketsComponent } from './student-travel-tickets.component';

const routes: Routes = [
    {
        path: '',
        component: StudentTravelTicketsComponent
    }
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule]
})
export class StudentTravelTicketsRoutingModule { }

