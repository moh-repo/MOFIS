import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SharedModule } from '../../../shared/shared.module';
import { ErrorRoutingModule } from './error-routing.module';
import { ErrorComponent } from './error.component';
import { ButtonModule } from 'primeng/button';

@NgModule({
    imports: [
        CommonModule,
        SharedModule,
        ErrorRoutingModule,
        ButtonModule
    ],
    declarations: [ErrorComponent]
})
export class ErrorModule { }
