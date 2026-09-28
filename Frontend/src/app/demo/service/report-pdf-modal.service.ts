import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';
import { ReportPdfQuery } from './report-pdf.service';

export interface ReportPdfModalRequest {
    pdfApiPath?: string;
    pdfQuery?: ReportPdfQuery;
    /**
     * Body for a report that is rendered from what the browser sends rather than
     * from what the server can look up — currently only the chart pages, whose
     * cards exist nowhere but the canvas that drew them. Present means POST.
     */
    pdfBody?: unknown;
    pdfBlob?: Blob;
    filename?: string;
    title?: string;
    /** Raise the print dialog as soon as the report has rendered (Print action). */
    print?: boolean;
}

@Injectable({ providedIn: 'root' })
export class ReportPdfModalService {

    private readonly openSubject = new Subject<ReportPdfModalRequest>();
    readonly open$ = this.openSubject.asObservable();

    open(request: ReportPdfModalRequest): void {
        this.openSubject.next(request);
    }
}
