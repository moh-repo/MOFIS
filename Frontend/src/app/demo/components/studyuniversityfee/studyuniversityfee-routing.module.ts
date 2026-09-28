import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { StudyUniversityFeeComponent } from './studyuniversityfee.component';

@NgModule({
    imports: [RouterModule.forChild([
        { path: '', component: StudyUniversityFeeComponent }
    ])],
    exports: [RouterModule]
})
export class StudyUniversityFeeRoutingModule { } 