package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.dto.StudentListDetailedDto;
import com.dilaw.mohe.dto.StudentListDetailedMapper;
import com.dilaw.mohe.model.StudentListDetailed;
import com.dilaw.mohe.report.pdf.PdfResponses;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.service.StudentListDetailedService;
import com.dilaw.mohe.service.StudentListMetadataService;
import com.dilaw.mohe.report.jasper.JasperReportService;
import com.dilaw.mohe.report.jasper.ReportParamBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports/student-list")
@PreAuthorize(MoheSecurityExpressions.STUDENT_PII)
public class StudentListDetailedController {

    @Autowired
    private StudentListDetailedService service;

    @Autowired
    private StudentListMetadataService metadataService;

    @Autowired
    private CountryScopeService countryScopeService;

    @Autowired
    private JasperReportService jasperReportService;

    @Autowired
    private ReportParamBuilder reportParamBuilder;

    @Autowired
    private com.dilaw.mohe.report.ReportExports reportExports;

    @GetMapping
    public ResponseEntity<Page<StudentListDetailedDto>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(StudentListDetailedMapper.toDtoPage(service.getAllPaginated(page, size, sortBy, sortDir)));
    }

    /** @deprecated Use paginated GET / instead. Returns at most 500 rows. */
    @GetMapping("/all")
    public ResponseEntity<List<StudentListDetailedDto>> getAllWithoutPagination(
            @RequestParam(defaultValue = "500") int maxSize) {
        int capped = Math.min(Math.max(maxSize, 1), 500);
        return ResponseEntity.ok(StudentListDetailedMapper.toDtoList(
                service.getAllPaginated(0, capped, "id", "desc").getContent()));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<StudentListDetailedDto>> getByFilters(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(required = false) Integer majorId,
            @RequestParam(required = false) Integer statusId,
            @RequestParam(required = false) Integer studyStatusId,
            @RequestParam(required = false) Integer genderId,
            @RequestParam(required = false) Integer degreeId,
            @RequestParam(required = false) Integer levelId,
            @RequestParam(required = false) Integer studyTypeId,
            @RequestParam(required = false) Integer universityTypeId,
            @RequestParam(required = false) Integer universityId,
            @RequestParam(required = false) Integer agencyTypeId,
            @RequestParam(required = false) Integer ministryTypeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "studentId") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(StudentListDetailedMapper.toDtoPage(service.getByFilters(
                countryId,
                countryIds,
                majorId,
                resolveStatusId(statusId, studyStatusId),
                genderId,
                degreeId,
                levelId,
                studyTypeId,
                resolveUniversityTypeId(universityTypeId, universityId),
                agencyTypeId,
                ministryTypeId,
                page,
                size,
                sortBy,
                sortDir)));
    }

    @GetMapping("/metadata")
    public ResponseEntity<?> getMetadata() {
        return ResponseEntity.ok(metadataService.getMetadata());
    }

    @GetMapping("/quick-search")
    public ResponseEntity<Page<StudentListDetailedDto>> searchStudents(
            @RequestParam String query,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(StudentListDetailedMapper.toDtoPage(
                service.searchStudents(query, countryId, countryIds, page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<StudentListDetailedDto> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(StudentListDetailedMapper.toDto(service.getById(id)));
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc", "/export-excel", "/report-xlsx"})
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds,
            @RequestParam(required = false) Integer majorId,
            @RequestParam(required = false) Integer statusId,
            @RequestParam(required = false) Integer studyStatusId,
            @RequestParam(required = false) Integer genderId,
            @RequestParam(required = false) Integer degreeId,
            @RequestParam(required = false) Integer levelId,
            @RequestParam(required = false) Integer studyTypeId,
            @RequestParam(required = false) Integer universityTypeId,
            @RequestParam(required = false) Integer universityId,
            @RequestParam(required = false) Integer agencyTypeId,
            @RequestParam(required = false) Integer ministryTypeId,
            @RequestParam(defaultValue = "ar") String lang) {
        List<StudentListDetailed> items = service.getByFilters(
                countryId,
                countryIds,
                majorId,
                resolveStatusId(statusId, studyStatusId),
                genderId,
                degreeId,
                levelId,
                studyTypeId,
                resolveUniversityTypeId(universityTypeId, universityId),
                agencyTypeId,
                ministryTypeId,
                0,
                500,
                "studentId",
                "asc").getContent();
        Map<String, Object> jasperParams = reportParamBuilder.build("STUDENT_LIST", lang);
        return reportExports.render("student-list", jasperParams, items, "student-list", disposition);
    }

    private static Integer resolveStatusId(Integer statusId, Integer studyStatusId) {
        return studyStatusId != null ? studyStatusId : statusId;
    }

    private static Integer resolveUniversityTypeId(Integer universityTypeId, Integer universityId) {
        return universityTypeId != null ? universityTypeId : universityId;
    }
}
