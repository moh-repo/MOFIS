import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import {StudentsComponent} from "./students.component";
import { StudentProfileComponent } from './student-profile.component';
import { validIdGuard } from '../../guards/valid-id.guard';

@NgModule({
    imports: [RouterModule.forChild([
        { path: '', component: StudentsComponent },
        { path: 'profile/:studentId', component: StudentProfileComponent, canActivate: [validIdGuard], data: { idParam: 'studentId' } }
    ])],
    exports: [RouterModule]
})
export class StudentsRoutingModule { }
