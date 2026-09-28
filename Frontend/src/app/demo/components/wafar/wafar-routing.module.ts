import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { WafarComponent } from './wafar.component';

const routes: Routes = [
    { path: '', component: WafarComponent }
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule]
})
export class WafarRoutingModule {}
