package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.dto.StudentFinancialLineDto;
import com.dilaw.mohe.dto.StudentProfileDocDto;
import com.dilaw.mohe.model.StudentListDetailed;
import com.dilaw.mohe.report.jasper.JasperReportService;
import com.dilaw.mohe.report.jasper.ReportParamBuilder;
import com.dilaw.mohe.report.pdf.PdfResponses;
import com.dilaw.mohe.service.IndividualStudentReportService;
import com.dilaw.mohe.service.StudentDocumentReportService;
import com.dilaw.mohe.service.StudentPhotoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports/individual-student")
@PreAuthorize(MoheSecurityExpressions.REPORT_ACADEMIC)
public class IndividualStudentReportController {

    @Autowired
    private IndividualStudentReportService service;

    @Autowired
    private StudentDocumentReportService documentReportService;

    @Autowired
    private StudentPhotoService studentPhotoService;

    @Autowired
    private JasperReportService jasperReportService;

    @Autowired
    private ReportParamBuilder reportParamBuilder;

    @Autowired
    private com.dilaw.mohe.report.ReportExports reportExports;

    @GetMapping(value = {"/{studentId}/export-report", "/{studentId}/report-pdf", "/{studentId}/doc"})
    public ResponseEntity<byte[]> exportReport(
            @PathVariable Integer studentId,
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(defaultValue = "ar") String lang) {

        StudentListDetailed student = service.getStudentForReport(studentId);
        Map<String, Object> params = reportParamBuilder.build("INDIVIDUAL_STUDENT", lang);
        byte[] pdf = jasperReportService.generatePdf("student-list", params, List.of(student));
        String filename = "individual-student-" + studentId + ".pdf";
        return PdfResponses.pdf(pdf, filename, PdfResponses.isInlineDisposition(disposition));
    }

    // ── Official single-student documents ─────────────────────────────────────

    @GetMapping(value = {"/{studentId}/id-card/export-report", "/{studentId}/id-card/report-pdf",
            "/{studentId}/id-card/doc"})
    public ResponseEntity<byte[]> idCard(
            @PathVariable Integer studentId,
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(defaultValue = "ar") String lang) {

        StudentProfileDocDto dto = documentReportService.getProfileDoc(studentId, lang);
        Map<String, Object> params = reportParamBuilder.build("STUDENT_ID_CARD", lang);
        addStudentPhoto(params, studentId);
        byte[] pdf = jasperReportService.generatePdf("student-id-card", params, List.of(dto));
        return PdfResponses.pdf(pdf, "student-id-card-" + studentId + ".pdf",
                PdfResponses.isInlineDisposition(disposition));
    }

    @GetMapping(value = {"/{studentId}/registration-form/export-report", "/{studentId}/registration-form/report-pdf",
            "/{studentId}/registration-form/doc"})
    public ResponseEntity<byte[]> registrationForm(
            @PathVariable Integer studentId,
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(defaultValue = "ar") String lang) {

        StudentProfileDocDto dto = documentReportService.getProfileDoc(studentId, lang);
        Map<String, Object> params = reportParamBuilder.build("STUDENT_REGISTRATION_FORM", lang);
        addStudentPhoto(params, studentId);
        byte[] pdf = jasperReportService.generatePdf("student-registration-form", params, List.of(dto));
        return PdfResponses.pdf(pdf, "student-registration-form-" + studentId + ".pdf",
                PdfResponses.isInlineDisposition(disposition));
    }

    @GetMapping(value = {"/{studentId}/enrollment-certificate/export-report",
            "/{studentId}/enrollment-certificate/report-pdf", "/{studentId}/enrollment-certificate/doc"})
    public ResponseEntity<byte[]> enrollmentCertificate(
            @PathVariable Integer studentId,
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(defaultValue = "ar") String lang) {

        StudentProfileDocDto dto = documentReportService.getProfileDoc(studentId, lang);
        Map<String, Object> params = reportParamBuilder.build("ENROLLMENT_CERTIFICATE", lang);
        // TITLE_01 is the resolved ministry/first header line — reuse it inside the prose.
        String ministryName = String.valueOf(params.getOrDefault("TITLE_01", ""));
        params.put("CERT_BODY", documentReportService.buildCertificateBody(dto, lang, ministryName));
        params.put("CERT_REF", "MOHE-" + studentId + "-" + params.getOrDefault("REPORT_DATE", ""));
        byte[] pdf = jasperReportService.generatePdf("enrollment-certificate", params, List.of(dto));
        return PdfResponses.pdf(pdf, "enrollment-certificate-" + studentId + ".pdf",
                PdfResponses.isInlineDisposition(disposition));
    }

    /**
     * Disbursement history is finance data, so this endpoint requires the finance
     * report permission instead of the academic one the rest of the class carries.
     * The method-level {@code @PreAuthorize} replaces the class-level expression
     * rather than adding to it, which is the intent here: an academic-only user
     * must not be able to pull a student's payment record.
     */
    /*
     * The statement is the one tab a spreadsheet makes sense for, so it answers on the
     * Excel aliases too (decision DEC-06). The report page offered Excel on every tab
     * and every one answered 404 (QA N17); the document tabs now hide the button.
     */
    @GetMapping(value = {"/{studentId}/financial-statement/export-report",
            "/{studentId}/financial-statement/report-pdf", "/{studentId}/financial-statement/doc",
            "/{studentId}/financial-statement/export-excel", "/{studentId}/financial-statement/report-xlsx"})
    @PreAuthorize(MoheSecurityExpressions.REPORT_FINANCE)
    public ResponseEntity<byte[]> financialStatement(
            @PathVariable Integer studentId,
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(defaultValue = "ar") String lang) {

        StudentProfileDocDto profile = documentReportService.getProfileDoc(studentId, lang);
        List<StudentFinancialLineDto> lines = documentReportService.getFinancialStatement(studentId, lang);
        StudentDocumentReportService.FinancialTotals totals =
                documentReportService.getFinancialTotals(studentId, lines);

        Map<String, Object> params = reportParamBuilder.build("STUDENT_FINANCIAL_STATEMENT", lang);
        params.put("STUDENT_NAME_HDR", profile.getStudentName());
        params.put("STUDENT_ID_HDR", String.valueOf(studentId));
        params.put("STUDENT_UNIVERSITY_HDR", profile.getStudyUniversityName());
        params.put("STUDENT_COUNTRY_HDR", profile.getStudyCountryName());
        params.put("TOTAL_RECEIVED", totals.received());
        params.put("TOTAL_WAFAR", totals.wafar());
        params.put("TOTAL_NET", totals.net());
        params.put("TOTAL_FEES", totals.fees());
        params.put("TOTAL_BOOKS", totals.books());
        params.put("TOTAL_LOANS", totals.loans());

        // The base name is also the access-log report code, so it carries no student id;
        // the browser names the download itself.
        return reportExports.render("student-financial-statement", params, lines,
                "student-financial-statement", disposition);
    }

    private void addStudentPhoto(Map<String, Object> params, Integer studentId) {
        byte[] photo = studentPhotoService.getPhotoBytes(studentId);
        if (photo != null && photo.length > 0) {
            params.put("STUDENT_PHOTO", new ByteArrayInputStream(photo));
        }
    }
}
