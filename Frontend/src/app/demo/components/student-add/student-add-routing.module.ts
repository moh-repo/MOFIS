import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { StudentAddComponent } from './student-add.component';

@NgModule({
    imports: [RouterModule.forChild([
        { path: '', component: StudentAddComponent },
        { path: 'edit/:id', component: StudentAddComponent }
    ])],
    exports: [RouterModule]
})
export class StudentAddRoutingModule { } 