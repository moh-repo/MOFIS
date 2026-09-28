import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { StudentExtensionComponent } from './student-extension.component';

const routes: Routes = [
    {
        path: '',
        component: StudentExtensionComponent,
        data: {
            breadcrumb: 'Student Extension'
        }
    }
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule]
})
export class StudentExtensionRoutingModule { }


