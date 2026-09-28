import { NgModule } from '@angular/core';
import { StudentAddRoutingModule } from './student-add-routing.module';
import { StudentAddFormModule } from './student-add-form.module';

/**
 * Lazy-loaded entry for full-page `/student-add` wizard.
 * Embedding the same wizard (e.g. modal on students list) imports {@link StudentAddFormModule} only.
 */
@NgModule({
    imports: [StudentAddFormModule, StudentAddRoutingModule],
})
export class StudentAddModule { }
