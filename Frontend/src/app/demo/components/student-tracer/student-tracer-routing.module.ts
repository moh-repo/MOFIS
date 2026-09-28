import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { StudentTracerComponent } from './student-tracer.component';

const routes: Routes = [
    {
        path: '',
        component: StudentTracerComponent,
        data: {
            breadcrumb: 'Student Tracer'
        }
    }
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule]
})
export class StudentTracerRoutingModule { }

