import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { FormsModule } from '@angular/forms';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { BidiModule } from '@angular/cdk/bidi';
import { InputTextModule } from 'primeng/inputtext';
import { DrawerModule } from 'primeng/drawer';
import { BadgeModule } from 'primeng/badge';
import { RadioButtonModule } from 'primeng/radiobutton';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { RippleModule } from 'primeng/ripple';
import { AppMenuComponent } from './app.menu.component';
import { AppMenuitemComponent } from './app.menuitem.component';
import { RouterModule } from '@angular/router';
import { AppTopBarComponent } from './app.topbar.component';
import { AppFooterComponent } from './app.footer.component';
import { AppConfigModule } from './config/config.module';
import { AppSidebarComponent } from "./app.sidebar.component";
import { AppLayoutComponent } from "./app.layout.component";
import { MenuModule } from 'primeng/menu';
import { TooltipModule } from 'primeng/tooltip';
import { SelectModule } from 'primeng/select';
import { PopoverModule } from 'primeng/popover';
import { DatePickerModule } from 'primeng/datepicker';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { SharedModule } from '../demo/shared/shared.module';

@NgModule({ declarations: [
        AppMenuitemComponent,
        AppTopBarComponent,
        AppFooterComponent,
        AppMenuComponent,
        AppSidebarComponent,
        AppLayoutComponent,
    ],
    exports: [AppLayoutComponent], imports: [BrowserModule,
        FormsModule,
        BrowserAnimationsModule,
        // For the `[dir]` directive on the layout wrapper: ng-zorro reads its
        // direction from the CDK Directionality, and only the directive keeps
        // that live across a language switch. See app.layout.component.html.
        BidiModule,
        InputTextModule,
        DrawerModule,
        BadgeModule,
        RadioButtonModule,
        ToggleSwitchModule,
        RippleModule,
        RouterModule,
        AppConfigModule,
        MenuModule,
        TooltipModule,
        SelectModule,
        PopoverModule,
        DatePickerModule,
        ConfirmDialogModule,
        SharedModule], providers: [provideHttpClient(withInterceptorsFromDi())] })
export class AppLayoutModule { }
