import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { LookupTablesComponent } from './lookuptables.component';

const routes: Routes = [
    {
        path: '',
        component: LookupTablesComponent
    }
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule]
})
export class LookupTablesRoutingModule { }

