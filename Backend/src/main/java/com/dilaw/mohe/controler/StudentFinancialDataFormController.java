package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.StudentFinancialDataForm;
import com.dilaw.mohe.report.jasper.JasperReportService;
import com.dilaw.mohe.report.jasper.ReportParamBuilder;
import com.dilaw.mohe.report.pdf.PdfResponses;
import com.dilaw.mohe.service.StudentFinancialDataFormService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports/financial-data-form")
@PreAuthorize(MoheSecurityExpressions.FINANCE)
public class StudentFinancialDataFormController {

    @Autowired
    private StudentFinancialDataFormService service;

    @Autowired
    private JasperReportService jasperReportService;

    @Autowired
    private ReportParamBuilder reportParamBuilder;

    @Autowired
    private com.dilaw.mohe.report.ReportExports reportExports;

    @GetMapping({"", "/"})
    public ResponseEntity<List<StudentFinancialDataForm>> getAll(
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getAll(countryId));
    }

    @GetMapping("/by-quarter-year")
    public ResponseEntity<List<StudentFinancialDataForm>> getByQuarterAndYear(
            @RequestParam Integer quarter,
            @RequestParam Integer year,
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getByQuarterAndYear(quarter, year, countryId));
    }

    @GetMapping("/by-country-quarter-year")
    public ResponseEntity<List<StudentFinancialDataForm>> getByCountryQuarterAndYear(
            @RequestParam String country,
            @RequestParam Integer quarter,
            @RequestParam Integer year,
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getByCountryQuarterAndYear(country, quarter, year, countryId));
    }

    @GetMapping("/metadata")
    public ResponseEntity<?> getMetadata(@RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getMetadata(countryId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<StudentFinancialDataForm> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc", "/export-excel", "/report-xlsx"})
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) Integer quarter,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "ar") String lang) {

        List<StudentFinancialDataForm> items;
        if (country != null && quarter != null && year != null) {
            items = service.getByCountryQuarterAndYear(country, quarter, year, countryId);
        } else if (quarter != null && year != null) {
            items = service.getByQuarterAndYear(quarter, year, countryId);
        } else {
            items = service.getAll(countryId);
        }

        Map<String, Object> params = reportParamBuilder.build("FINANCIAL_DATA_FORM", lang);
        params.put("FILTER_YEAR", year != null ? String.valueOf(year) : "");
        params.put("FILTER_QUARTER", quarter != null ? String.valueOf(quarter) : "");
        params.put("FILTER_COUNTRY", country != null ? country : "");

        return reportExports.render("financial-data-form", params, items, "financial-data-form", disposition);
    }
}
