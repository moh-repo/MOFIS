import { NgModule } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe, registerLocaleData } from '@angular/common';
import localeArEg from '@angular/common/locales/ar-EG';
import { TranslatePipe } from '../pipes/translate.pipe';
import { LocaleDatePipe } from '../pipes/locale-date.pipe';
import { LocaleNumberPipe } from '../pipes/locale-number.pipe';
import { CodeIconPipe, CodeLabelPipe, CodeSeverityPipe } from '../pipes/code-pipes';
import { InLanguagePipe } from '../pipes/in-language.pipe';
import { ReportViewerComponent } from './report-viewer/report-viewer.component';
import { ReportPdfModalComponent } from './report-pdf-modal/report-pdf-modal.component';
import { ReportActionsComponent } from './report-actions/report-actions.component';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';

// Angular ships only en-US locale data. LocaleDatePipe and LocaleNumberPipe
// format Arabic as 'ar-EG', which throws NG0701 unless its data is registered.
// Registered here, next to the pipes SharedModule exports, so that any module
// importing SharedModule gets working pipes without extra setup.
registerLocaleData(localeArEg, 'ar-EG');

@NgModule({
  declarations: [
    ReportViewerComponent,
    ReportPdfModalComponent,
    ReportActionsComponent
  ],
  imports: [
    CommonModule,
    TranslatePipe,
    LocaleDatePipe,
    LocaleNumberPipe,
    CodeLabelPipe,
    CodeSeverityPipe,
    CodeIconPipe,
    InLanguagePipe,
    ProgressSpinnerModule,
    DialogModule,
    ButtonModule
  ],
  exports: [
    TranslatePipe,
    LocaleDatePipe,
    LocaleNumberPipe,
    // Code-list pipes: names, tag colours and icons from the loaded lists, so
    // no feature module needs a code→colour switch of its own.
    CodeLabelPipe,
    CodeSeverityPipe,
    CodeIconPipe,
    InLanguagePipe,
    ReportViewerComponent,
    ReportPdfModalComponent,
    ReportActionsComponent
  ],
  // LocaleDatePipe and LocaleNumberPipe inject DatePipe/DecimalPipe, which
  // CommonModule exports but does not provide. Providing them here means any
  // module that imports SharedModule can use the pipes in its templates without
  // having to know about the dependency.
  providers: [DatePipe, DecimalPipe]
})
export class SharedModule { }
