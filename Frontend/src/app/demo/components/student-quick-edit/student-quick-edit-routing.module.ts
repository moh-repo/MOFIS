import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { StudentQuickEditComponent } from './student-quick-edit.component';

@NgModule({
    imports: [RouterModule.forChild([
        { path: '', component: StudentQuickEditComponent }
    ])],
    exports: [RouterModule]
})
export class StudentQuickEditRoutingModule { }

