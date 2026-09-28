import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SharedModule } from '../../../shared/shared.module';
import { ButtonModule } from 'primeng/button';

import { AccessRoutingModule } from './access-routing.module';
import { AccessComponent } from './access.component';

@NgModule({
    imports: [
        CommonModule,
        SharedModule,
        AccessRoutingModule,
        ButtonModule
    ],
    declarations: [AccessComponent]
})
export class AccessModule { }
