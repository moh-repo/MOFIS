package com.dilaw.mohe.controler;

import com.dilaw.mohe.model.*;
import com.dilaw.mohe.service.*;
import com.dilaw.mohe.util.LookupTablePageUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import com.dilaw.mohe.config.MoheSecurityExpressions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@PreAuthorize(MoheSecurityExpressions.LOOKUP_TABLE)
@RestController
@RequestMapping("api/lookup-tables")
public class LookupTableController {

    @Autowired
    private StudentGenderService studentGenderService;

    @Autowired
    private StudyReceivedWhomStatusService studyReceivedWhomStatusService;

    @Autowired
    private MoheMainService moheMainService;

    @Autowired
    private StudentDegreeService studentDegreeService;

    @Autowired
    private StudentCurrentLevelService studentCurrentLevelService;

    @Autowired
    private StudyStatusService studyStatusService;

    @Autowired
    private StudyDegreeInService studyDegreeInService;

    @Autowired
    private StudentUniversityTypeService studentUniversityTypeService;

    @Autowired
    private StudentAgencyTypeService studentAgencyTypeService;

    @Autowired
    private MoheSubMainService moheSubMainService;

    @Autowired
    private StudentCountryService studentCountryService;

    @Autowired
    private StudentGovService studentGovService;

    @Autowired
    private StudentGovMudService studentGovMudService;

    // Add missing services
    @Autowired
    private StudentMajorService studentMajorService;

    @Autowired
    private StudentStudyTypeService studentStudyTypeService;

    @Autowired
    private StudentMinistryTypeService studentMinistryTypeService;

    @Autowired
    private StudyCountryInService studyCountryInService;

    @Autowired
    private StudyGovInService studyGovInService;

    @Autowired
    private StudyUniversityInService studyUniversityInService;

    @Autowired
    private StudyCollegeInService studyCollegeInService;

    @Autowired
    private StudyEductionInService studyEductionInService;

    @Autowired
    private StudyMajorInService studyMajorInService;

    @Autowired
    private StudentStudyStatusService studentStudyStatusService;

    @Autowired
    private StudyCurrencyInService studyCurrencyInService;

    // New status/type lookup services
    @Autowired
    private LuWafarSpendingStatusService luWafarSpendingStatusService;

    @Autowired
    private LuWafarReconciliationStatusService luWafarReconciliationStatusService;

    @Autowired
    private LuPaymentAuthStatusService luPaymentAuthStatusService;

    @Autowired
    private LuPaymentAmountSourceService luPaymentAmountSourceService;

    @Autowired
    private LuChangeRequestStatusService luChangeRequestStatusService;

    @Autowired
    private LuStudentPortalAccountStatusService luStudentPortalAccountStatusService;

    @Autowired
    private LuNotificationChannelService luNotificationChannelService;

    @Autowired
    private LuNotificationRecipientTypeService luNotificationRecipientTypeService;

    @Autowired
    private LuNotificationScopeService luNotificationScopeService;

    @Autowired
    private LuQuarterNumberService luQuarterNumberService;

    @Autowired
    private StudentCountryAllService studentCountryAllService;

    @Autowired
    private BusinessLookupService businessLookupService;

    @Autowired
    private LuExtendedLookupAdminService luExtendedLookupAdminService;

    // Student Gender endpoints
    @GetMapping("/student-gender")
    public ResponseEntity<Map<String, Object>> getAllStudentGenders(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        return getLookupTableData(studentGenderService, page, size, search, sortBy, sortDir);
    }

    @GetMapping("/student-gender/{id}")
    public ResponseEntity<StudentGender> getStudentGenderById(@PathVariable Integer id) {
        return ResponseEntity.ok(studentGenderService.getById(id));
    }

    @PostMapping("/student-gender")
    public ResponseEntity<StudentGender> createStudentGender(@Valid @RequestBody StudentGender entity) {
        return ResponseEntity.ok(studentGenderService.create(entity));
    }

    @PutMapping("/student-gender/{id}")
    public ResponseEntity<StudentGender> updateStudentGender(@PathVariable Integer id,
            @Valid @RequestBody StudentGender entity) {
        return ResponseEntity.ok(studentGenderService.update(id, entity));
    }

    @DeleteMapping("/student-gender/{id}")
    public ResponseEntity<Void> deleteStudentGender(@PathVariable Integer id) {
        studentGenderService.delete(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/student-gender/batch")
    public ResponseEntity<Void> deleteStudentGenders(@Valid @RequestBody Map<String, List<Integer>> request) {
        List<Integer> ids = request.get("ids");
        for (Integer id : ids) {
            studentGenderService.delete(id);
        }
        return ResponseEntity.ok().build();
    }

    // Study Received Whom Status endpoints
    @GetMapping("/study-received-whom-status")
    public ResponseEntity<Map<String, Object>> getAllStudyReceivedWhomStatuses(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        return getLookupTableData(studyReceivedWhomStatusService, page, size, search, sortBy, sortDir);
    }

    @GetMapping("/study-received-whom-status/{id}")
    public ResponseEntity<StudyReceivedWhomStatus> getStudyReceivedWhomStatusById(@PathVariable Integer id) {
        return ResponseEntity.ok(studyReceivedWhomStatusService.getById(id));
    }

    @PostMapping("/study-received-whom-status")
    public ResponseEntity<StudyReceivedWhomStatus> createStudyReceivedWhomStatus(
            @Valid @RequestBody StudyReceivedWhomStatus entity) {
        return ResponseEntity.ok(studyReceivedWhomStatusService.create(entity));
    }

    @PutMapping("/study-received-whom-status/{id}")
    public ResponseEntity<StudyReceivedWhomStatus> updateStudyReceivedWhomStatus(@PathVariable Integer id,
            @Valid @RequestBody StudyReceivedWhomStatus entity) {
        return ResponseEntity.ok(studyReceivedWhomStatusService.update(id, entity));
    }

    @DeleteMapping("/study-received-whom-status/{id}")
    public ResponseEntity<Void> deleteStudyReceivedWhomStatus(@PathVariable Integer id) {
        studyReceivedWhomStatusService.delete(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/study-received-whom-status/batch")
    public ResponseEntity<Void> deleteStudyReceivedWhomStatuses(@Valid @RequestBody Map<String, List<Integer>> request) {
        List<Integer> ids = request.get("ids");
        for (Integer id : ids) {
            studyReceivedWhomStatusService.delete(id);
        }
        return ResponseEntity.ok().build();
    }

    // MOHE Main endpoints
    @GetMapping("/mohe-main")
    public ResponseEntity<Map<String, Object>> getAllMoheMains(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        return getLookupTableData(moheMainService, page, size, search, sortBy, sortDir);
    }

    @GetMapping("/mohe-main/{id}")
    public ResponseEntity<MoheMain> getMoheMainById(@PathVariable Integer id) {
        return ResponseEntity.ok(moheMainService.getById(id));
    }

    @PostMapping("/mohe-main")
    public ResponseEntity<MoheMain> createMoheMain(@Valid @RequestBody MoheMain entity) {
        return ResponseEntity.ok(moheMainService.create(entity));
    }

    @PutMapping("/mohe-main/{id}")
    public ResponseEntity<MoheMain> updateMoheMain(@PathVariable Integer id, @Valid @RequestBody MoheMain entity) {
        return ResponseEntity.ok(moheMainService.update(id, entity));
    }

    @DeleteMapping("/mohe-main/{id}")
    public ResponseEntity<Void> deleteMoheMain(@PathVariable Integer id) {
        moheMainService.delete(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/mohe-main/batch")
    public ResponseEntity<Void> deleteMoheMains(@Valid @RequestBody Map<String, List<Integer>> request) {
        List<Integer> ids = request.get("ids");
        for (Integer id : ids) {
            moheMainService.delete(id);
        }
        return ResponseEntity.ok().build();
    }

    // Student Degree endpoints
    @GetMapping("/student-degree")
    public ResponseEntity<Map<String, Object>> getAllStudentDegrees(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        return getLookupTableData(studentDegreeService, page, size, search, sortBy, sortDir);
    }

    @GetMapping("/student-degree/{id}")
    public ResponseEntity<StudentDegree> getStudentDegreeById(@PathVariable Integer id) {
        return ResponseEntity.ok(studentDegreeService.getById(id));
    }

    @PostMapping("/student-degree")
    public ResponseEntity<StudentDegree> createStudentDegree(@Valid @RequestBody StudentDegree entity) {
        return ResponseEntity.ok(studentDegreeService.create(entity));
    }

    @PutMapping("/student-degree/{id}")
    public ResponseEntity<StudentDegree> updateStudentDegree(@PathVariable Integer id,
            @Valid @RequestBody StudentDegree entity) {
        return ResponseEntity.ok(studentDegreeService.update(id, entity));
    }

    @DeleteMapping("/student-degree/{id}")
    public ResponseEntity<Void> deleteStudentDegree(@PathVariable Integer id) {
        studentDegreeService.delete(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/student-degree/batch")
    public ResponseEntity<Void> deleteStudentDegrees(@Valid @RequestBody Map<String, List<Integer>> request) {
        List<Integer> ids = request.get("ids");
        for (Integer id : ids) {
            studentDegreeService.delete(id);
        }
        return ResponseEntity.ok().build();
    }

    // Student Current Level endpoints
    @GetMapping("/student-current-level")
    public ResponseEntity<Map<String, Object>> getAllStudentCurrentLevels(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        return getLookupTableData(studentCurrentLevelService, page, size, search, sortBy, sortDir);
    }

    @GetMapping("/student-current-level/{id}")
    public ResponseEntity<StudentCurrentLevel> getStudentCurrentLevelById(@PathVariable Integer id) {
        return ResponseEntity.ok(studentCurrentLevelService.getById(id));
    }

    @PostMapping("/student-current-level")
    public ResponseEntity<StudentCurrentLevel> createStudentCurrentLevel(@Valid @RequestBody StudentCurrentLevel entity) {
        return ResponseEntity.ok(studentCurrentLevelService.create(entity));
    }

    @PutMapping("/student-current-level/{id}")
    public ResponseEntity<StudentCurrentLevel> updateStudentCurrentLevel(@PathVariable Integer id,
            @Valid @RequestBody StudentCurrentLevel entity) {
        return ResponseEntity.ok(studentCurrentLevelService.update(id, entity));
    }

    @DeleteMapping("/student-current-level/{id}")
    public ResponseEntity<Void> deleteStudentCurrentLevel(@PathVariable Integer id) {
        studentCurrentLevelService.delete(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/student-current-level/batch")
    public ResponseEntity<Void> deleteStudentCurrentLevels(@Valid @RequestBody Map<String, List<Integer>> request) {
        List<Integer> ids = request.get("ids");
        for (Integer id : ids) {
            studentCurrentLevelService.delete(id);
        }
        return ResponseEntity.ok().build();
    }

    // Study Status endpoints
    @GetMapping("/study-status")
    public ResponseEntity<Map<String, Object>> getAllStudyStatuses(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        return getLookupTableData(studyStatusService, page, size, search, sortBy, sortDir);
    }

    @GetMapping("/study-status/{id}")
    public ResponseEntity<StudyStatus> getStudyStatusById(@PathVariable Integer id) {
        return ResponseEntity.ok(studyStatusService.getById(id));
    }

    @PostMapping("/study-status")
    public ResponseEntity<StudyStatus> createStudyStatus(@Valid @RequestBody StudyStatus entity) {
        return ResponseEntity.ok(studyStatusService.create(entity));
    }

    @PutMapping("/study-status/{id}")
    public ResponseEntity<StudyStatus> updateStudyStatus(@PathVariable Integer id, @Valid @RequestBody StudyStatus entity) {
        return ResponseEntity.ok(studyStatusService.update(id, entity));
    }

    @DeleteMapping("/study-status/{id}")
    public ResponseEntity<Void> deleteStudyStatus(@PathVariable Integer id) {
        studyStatusService.delete(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/study-status/batch")
    public ResponseEntity<Void> deleteStudyStatuses(@Valid @RequestBody Map<String, List<Integer>> request) {
        List<Integer> ids = request.get("ids");
        for (Integer id : ids) {
            studyStatusService.delete(id);
        }
        return ResponseEntity.ok().build();
    }

    // Study Degree In endpoints
    @GetMapping("/study-degree-in")
    public ResponseEntity<Map<String, Object>> getAllStudyDegreeIns(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        return getLookupTableData(studyDegreeInService, page, size, search, sortBy, sortDir);
    }

    @GetMapping("/study-degree-in/{id}")
    public ResponseEntity<StudyDegreeIn> getStudyDegreeInById(@PathVariable Integer id) {
        return ResponseEntity.ok(studyDegreeInService.getById(id));
    }

    @PostMapping("/study-degree-in")
    public ResponseEntity<StudyDegreeIn> createStudyDegreeIn(@Valid @RequestBody StudyDegreeIn entity) {
        return ResponseEntity.ok(studyDegreeInService.create(entity));
    }

    @PutMapping("/study-degree-in/{id}")
    public ResponseEntity<StudyDegreeIn> updateStudyDegreeIn(@PathVariable Integer id,
            @Valid @RequestBody StudyDegreeIn entity) {
        return ResponseEntity.ok(studyDegreeInService.update(id, entity));
    }

    @DeleteMapping("/study-degree-in/{id}")
    public ResponseEntity<Void> deleteStudyDegreeIn(@PathVariable Integer id) {
        studyDegreeInService.delete(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/study-degree-in/batch")
    public ResponseEntity<Void> deleteStudyDegreeIns(@Valid @RequestBody Map<String, List<Integer>> request) {
        List<Integer> ids = request.get("ids");
        for (Integer id : ids) {
            studyDegreeInService.delete(id);
        }
        return ResponseEntity.ok().build();
    }

    // Student University Type endpoints
    @GetMapping("/student-university-type")
    public ResponseEntity<Map<String, Object>> getAllStudentUniversityTypes(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        return getLookupTableData(studentUniversityTypeService, page, size, search, sortBy, sortDir);
    }

    @GetMapping("/student-university-type/{id}")
    public ResponseEntity<StudentUniversityType> getStudentUniversityTypeById(@PathVariable Integer id) {
        return ResponseEntity.ok(studentUniversityTypeService.getById(id));
    }

    @PostMapping("/student-university-type")
    public ResponseEntity<StudentUniversityType> createStudentUniversityType(
            @Valid @RequestBody StudentUniversityType entity) {
        return ResponseEntity.ok(studentUniversityTypeService.create(entity));
    }

    @PutMapping("/student-university-type/{id}")
    public ResponseEntity<StudentUniversityType> updateStudentUniversityType(@PathVariable Integer id,
            @Valid @RequestBody StudentUniversityType entity) {
        return ResponseEntity.ok(studentUniversityTypeService.update(id, entity));
    }

    @DeleteMapping("/student-university-type/{id}")
    public ResponseEntity<Void> deleteStudentUniversityType(@PathVariable Integer id) {
        studentUniversityTypeService.delete(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/student-university-type/batch")
    public ResponseEntity<Void> deleteStudentUniversityTypes(@Valid @RequestBody Map<String, List<Integer>> request) {
        List<Integer> ids = request.get("ids");
        for (Integer id : ids) {
            studentUniversityTypeService.delete(id);
        }
        return ResponseEntity.ok().build();
    }

    // Student Agency Type endpoints
    @GetMapping("/student-agency-type")
    public ResponseEntity<Map<String, Object>> getAllStudentAgencyTypes(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        return getLookupTableData(studentAgencyTypeService, page, size, search, sortBy, sortDir);
    }

    @GetMapping("/student-agency-type/{id}")
    public ResponseEntity<StudentAgencyType> getStudentAgencyTypeById(@PathVariable Integer id) {
        return ResponseEntity.ok(studentAgencyTypeService.getById(id));
    }

    @PostMapping("/student-agency-type")
    public ResponseEntity<StudentAgencyType> createStudentAgencyType(@Valid @RequestBody StudentAgencyType entity) {
        return ResponseEntity.ok(studentAgencyTypeService.create(entity));
    }

    @PutMapping("/student-agency-type/{id}")
    public ResponseEntity<StudentAgencyType> updateStudentAgencyType(@PathVariable Integer id,
            @Valid @RequestBody StudentAgencyType entity) {
        return ResponseEntity.ok(studentAgencyTypeService.update(id, entity));
    }

    @DeleteMapping("/student-agency-type/{id}")
    public ResponseEntity<Void> deleteStudentAgencyType(@PathVariable Integer id) {
        studentAgencyTypeService.delete(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/student-agency-type/batch")
    public ResponseEntity<Void> deleteStudentAgencyTypes(@Valid @RequestBody Map<String, List<Integer>> request) {
        List<Integer> ids = request.get("ids");
        for (Integer id : ids) {
            studentAgencyTypeService.delete(id);
        }
        return ResponseEntity.ok().build();
    }

    // MOHE Sub Main endpoints
    @GetMapping("/mohe-sub-main")
    public ResponseEntity<Map<String, Object>> getAllMoheSubMains(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        return getLookupTableData(moheSubMainService, page, size, search, sortBy, sortDir);
    }

    @GetMapping("/mohe-sub-main/{id}")
    public ResponseEntity<MoheSubMain> getMoheSubMainById(@PathVariable Integer id) {
        return ResponseEntity.ok(moheSubMainService.getById(id));
    }

    @PostMapping("/mohe-sub-main")
    public ResponseEntity<MoheSubMain> createMoheSubMain(@Valid @RequestBody MoheSubMain entity) {
        return ResponseEntity.ok(moheSubMainService.create(entity));
    }

    @PutMapping("/mohe-sub-main/{id}")
    public ResponseEntity<MoheSubMain> updateMoheSubMain(@PathVariable Integer id, @Valid @RequestBody MoheSubMain entity) {
        return ResponseEntity.ok(moheSubMainService.update(id, entity));
    }

    @DeleteMapping("/mohe-sub-main/{id}")
    public ResponseEntity<Void> deleteMoheSubMain(@PathVariable Integer id) {
        moheSubMainService.delete(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/mohe-sub-main/batch")
    public ResponseEntity<Void> deleteMoheSubMains(@Valid @RequestBody Map<String, List<Integer>> request) {
        List<Integer> ids = request.get("ids");
        for (Integer id : ids) {
            moheSubMainService.delete(id);
        }
        return ResponseEntity.ok().build();
    }

    // Country endpoints
    @GetMapping("/country")
    public ResponseEntity<Map<String, Object>> getAllCountries(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        return getLookupTableData(studentCountryService, page, size, search, sortBy, sortDir);
    }

    @GetMapping("/country/{id}")
    public ResponseEntity<StudentCountry> getCountryById(@PathVariable Integer id) {
        return ResponseEntity.ok(studentCountryService.getStudentCountryById(id));
    }

    @PostMapping("/country")
    public ResponseEntity<StudentCountry> createCountry(@Valid @RequestBody StudentCountry entity) {
        return ResponseEntity.ok(studentCountryService.createStudentCountry(entity));
    }

    @PutMapping("/country/{id}")
    public ResponseEntity<StudentCountry> updateCountry(@PathVariable Integer id, @Valid @RequestBody StudentCountry entity) {
        return ResponseEntity.ok(studentCountryService.updateStudentCountry(id, entity));
    }

    @DeleteMapping("/country/{id}")
    public ResponseEntity<Void> deleteCountry(@PathVariable Integer id) {
        studentCountryService.deleteStudentCountry(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/country/batch")
    public ResponseEntity<Void> deleteCountries(@Valid @RequestBody Map<String, List<Integer>> request) {
        List<Integer> ids = request.get("ids");
        for (Integer id : ids) {
            studentCountryService.deleteStudentCountry(id);
        }
        return ResponseEntity.ok().build();
    }

    // Governorate endpoints
    @GetMapping("/governorate")
    public ResponseEntity<Map<String, Object>> getAllGovernorates(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        return getLookupTableData(studentGovService, page, size, search, sortBy, sortDir);
    }

    @GetMapping("/governorate/{id}")
    public ResponseEntity<StudentGov> getGovernorateById(@PathVariable Integer id) {
        return ResponseEntity.ok(studentGovService.getById(id));
    }

    @PostMapping("/governorate")
    public ResponseEntity<StudentGov> createGovernorate(@Valid @RequestBody StudentGov entity) {
        return ResponseEntity.ok(studentGovService.create(entity));
    }

    @PutMapping("/governorate/{id}")
    public ResponseEntity<StudentGov> updateGovernorate(@PathVariable Integer id, @Valid @RequestBody StudentGov entity) {
        return ResponseEntity.ok(studentGovService.update(id, entity));
    }

    @DeleteMapping("/governorate/{id}")
    public ResponseEntity<Void> deleteGovernorate(@PathVariable Integer id) {
        studentGovService.delete(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/governorate/batch")
    public ResponseEntity<Void> deleteGovernorates(@Valid @RequestBody Map<String, List<Integer>> request) {
        List<Integer> ids = request.get("ids");
        for (Integer id : ids) {
            studentGovService.delete(id);
        }
        return ResponseEntity.ok().build();
    }

    // Municipality endpoints
    @GetMapping("/municipality")
    public ResponseEntity<Map<String, Object>> getAllMunicipalities(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer govId,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        if (govId != null) {
            List<StudentGovMud> data = countryId != null
                    ? studentGovMudService.getAllByGovIdAndCountryId(govId, countryId)
                    : studentGovMudService.getAllByGovId(govId);
            Map<String, Object> response = new HashMap<>();
            response.put("content", data);
            response.put("totalElements", data.size());
            return ResponseEntity.ok(response);
        }
        return getLookupTableData(studentGovMudService, page, size, search, sortBy, sortDir);
    }

    @GetMapping("/municipality/{id}")
    public ResponseEntity<StudentGovMud> getMunicipalityById(@PathVariable Integer id) {
        return ResponseEntity.ok(studentGovMudService.getById(id));
    }

    @PostMapping("/municipality")
    public ResponseEntity<StudentGovMud> createMunicipality(@Valid @RequestBody StudentGovMud entity) {
        return ResponseEntity.ok(studentGovMudService.create(entity));
    }

    @PutMapping("/municipality/{id}")
    public ResponseEntity<StudentGovMud> updateMunicipality(@PathVariable Integer id,
            @Valid @RequestBody StudentGovMud entity) {
        return ResponseEntity.ok(studentGovMudService.update(id, entity));
    }

    @DeleteMapping("/municipality/{id}")
    public ResponseEntity<Void> deleteMunicipality(@PathVariable Integer id) {
        studentGovMudService.delete(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/municipality/batch")
    public ResponseEntity<Void> deleteMunicipalities(@Valid @RequestBody Map<String, List<Integer>> request) {
        List<Integer> ids = request.get("ids");
        for (Integer id : ids) {
            studentGovMudService.delete(id);
        }
        return ResponseEntity.ok().build();
    }

    /**
     * A bilingual {@code LU_*} list, as a list or as a page.
     *
     * <p>These URLs answer two callers. A dropdown asks for the list and gets the
     * list, the way it always has — {@code LookupService} fetches a dozen of them
     * that way. The Lookup Tables screen asks for a page: it sends {@code page} and
     * {@code size} on every lazy load, and until now got the whole list whatever it
     * asked for, so Report Labels put all 301 of its rows on a page whose paginator
     * read "1 to 10 of 301", and its search box narrowed nothing.
     */
    private ResponseEntity<?> listOrPage(List<?> data, Integer page, Integer size,
            String search, String sortBy, String sortDir) {
        if (page == null && size == null) {
            return ResponseEntity.ok(data);
        }
        return ResponseEntity.ok(LookupTablePageUtil.page(
                data, page != null ? page : 0, size != null ? size : 10, search, sortBy, sortDir));
    }

    /**
     * Pagination, search and sort for a lookup table, in memory over the whole
     * list — which is what these tables are the right size for; the largest is 444
     * rows.
     *
     * <p>There used to be a four-argument overload beside this one that passed
     * null for sortBy and sortDir, and every endpoint but one called it. The
     * Lookup Tables screen sends the column it was asked to sort by on every lazy
     * load, so a click on a column header re-fetched the same page in the same
     * order and looked like nothing had happened.
     */
    private ResponseEntity<Map<String, Object>> getLookupTableData(
            Object service, Integer page, Integer size, String search, String sortBy, String sortDir) {
        return pageOrWhole(extractLookupData(service), page, size, search, sortBy, sortDir);
    }

    private ResponseEntity<Map<String, Object>> pageOrWhole(
            List<?> data, Integer page, Integer size, String search, String sortBy, String sortDir) {
        // A caller that asks for no page gets the whole table, in the same shape (live QA
        // M7/M8): the default of ten rows cut every dropdown and report filter that called
        // without paging at ten (16 study countries, 21 levels). The Lookup Tables screen
        // always sends page and size, and pages as before.
        if (page == null && size == null) {
            return ResponseEntity.ok(LookupTablePageUtil.whole(data, search, sortBy, sortDir));
        }
        return ResponseEntity.ok(LookupTablePageUtil.page(
                data, page != null ? page : 0, size != null ? size : 10, search, sortBy, sortDir));
    }

    private List<?> extractLookupData(Object service) {
        if (service instanceof StudentGenderService s) {
            return s.getAll();
        } else if (service instanceof StudyReceivedWhomStatusService s) {
            return s.getAll();
        } else if (service instanceof MoheMainService s) {
            return s.getAll();
        } else if (service instanceof StudentDegreeService s) {
            return s.getAll();
        } else if (service instanceof StudentCurrentLevelService s) {
            return s.getAll();
        } else if (service instanceof StudyStatusService s) {
            return s.getAll();
        } else if (service instanceof StudyDegreeInService s) {
            return s.getAll();
        } else if (service instanceof StudentUniversityTypeService s) {
            return s.getAll();
        } else if (service instanceof StudentAgencyTypeService s) {
            return s.getAll();
        } else if (service instanceof MoheSubMainService s) {
            return s.getAll();
        } else if (service instanceof StudentCountryService s) {
            return s.getStudentCountriesAll();
        } else if (service instanceof StudentGovService s) {
            return s.getAll();
        } else if (service instanceof StudentGovMudService s) {
            return s.getAll();
        } else if (service instanceof StudentMajorService s) {
            return s.getAll();
        } else if (service instanceof StudentStudyTypeService s) {
            return s.getAll();
        } else if (service instanceof StudentMinistryTypeService s) {
            return s.getAll();
        } else if (service instanceof StudyCountryInService s) {
            return s.getAll();
        } else if (service instanceof StudyGovInService s) {
            return s.getAll();
        } else if (service instanceof StudyUniversityInService s) {
            return s.getAll();
        } else if (service instanceof StudyCollegeInService s) {
            return s.getAll();
        } else if (service instanceof StudyEductionInService s) {
            return s.getAll();
        } else if (service instanceof StudyMajorInService s) {
            return s.getAll();
        } else if (service instanceof StudentStudyStatusService s) {
            return s.getAll();
        } else if (service instanceof StudyCurrencyInService s) {
            return s.getAll();
        }
        return List.of();
    }

    // Student Major endpoints
    @GetMapping("/student-major")
    public ResponseEntity<Map<String, Object>> getAllStudentMajors(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        return getLookupTableData(studentMajorService, page, size, search, sortBy, sortDir);
    }

    @GetMapping("/student-major/{id}")
    public ResponseEntity<StudentMajor> getStudentMajorById(@PathVariable Integer id) {
        return ResponseEntity.ok(studentMajorService.getById(id));
    }

    @PostMapping("/student-major")
    public ResponseEntity<StudentMajor> createStudentMajor(@Valid @RequestBody StudentMajor entity) {
        return ResponseEntity.ok(studentMajorService.create(entity));
    }

    @PutMapping("/student-major/{id}")
    public ResponseEntity<StudentMajor> updateStudentMajor(@PathVariable Integer id, @Valid @RequestBody StudentMajor entity) {
        return ResponseEntity.ok(studentMajorService.update(id, entity));
    }

    @DeleteMapping("/student-major/{id}")
    public ResponseEntity<Void> deleteStudentMajor(@PathVariable Integer id) {
        studentMajorService.delete(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/student-major/batch")
    public ResponseEntity<Void> deleteStudentMajors(@Valid @RequestBody Map<String, List<Integer>> request) {
        List<Integer> ids = request.get("ids");
        for (Integer id : ids) {
            studentMajorService.delete(id);
        }
        return ResponseEntity.ok().build();
    }

    // Study Currency In endpoints
    @GetMapping("/study-currency-in")
    public ResponseEntity<Map<String, Object>> getAllStudyCurrencyIns(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        return getLookupTableData(studyCurrencyInService, page, size, search, sortBy, sortDir);
    }

    @GetMapping("/study-currency-in/{id}")
    public ResponseEntity<StudyCurrencyIn> getStudyCurrencyInById(@PathVariable Integer id) {
        return ResponseEntity.ok(studyCurrencyInService.getById(id));
    }

    @PostMapping("/study-currency-in")
    public ResponseEntity<StudyCurrencyIn> createStudyCurrencyIn(@Valid @RequestBody StudyCurrencyIn entity) {
        return ResponseEntity.ok(studyCurrencyInService.create(entity));
    }

    @PutMapping("/study-currency-in/{id}")
    public ResponseEntity<StudyCurrencyIn> updateStudyCurrencyIn(@PathVariable Integer id,
            @Valid @RequestBody StudyCurrencyIn entity) {
        return ResponseEntity.ok(studyCurrencyInService.update(id, entity));
    }

    @DeleteMapping("/study-currency-in/{id}")
    public ResponseEntity<Void> deleteStudyCurrencyIn(@PathVariable Integer id) {
        studyCurrencyInService.delete(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/study-currency-in/batch")
    public ResponseEntity<Void> deleteStudyCurrencyIns(@Valid @RequestBody Map<String, List<Integer>> request) {
        List<Integer> ids = request.get("ids");
        for (Integer id : ids) {
            studyCurrencyInService.delete(id);
        }
        return ResponseEntity.ok().build();
    }

    // Student Study Type endpoints
    @GetMapping("/student-study-type")
    public ResponseEntity<Map<String, Object>> getAllStudentStudyTypes(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        return getLookupTableData(studentStudyTypeService, page, size, search, sortBy, sortDir);
    }

    @GetMapping("/student-study-type/{id}")
    public ResponseEntity<StudentStudyType> getStudentStudyTypeById(@PathVariable Integer id) {
        return ResponseEntity.ok(studentStudyTypeService.getById(id));
    }

    @PostMapping("/student-study-type")
    public ResponseEntity<StudentStudyType> createStudentStudyType(@Valid @RequestBody StudentStudyType entity) {
        return ResponseEntity.ok(studentStudyTypeService.create(entity));
    }

    @PutMapping("/student-study-type/{id}")
    public ResponseEntity<StudentStudyType> updateStudentStudyType(@PathVariable Integer id,
            @Valid @RequestBody StudentStudyType entity) {
        return ResponseEntity.ok(studentStudyTypeService.update(id, entity));
    }

    @DeleteMapping("/student-study-type/{id}")
    public ResponseEntity<Void> deleteStudentStudyType(@PathVariable Integer id) {
        studentStudyTypeService.delete(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/student-study-type/batch")
    public ResponseEntity<Void> deleteStudentStudyTypes(@Valid @RequestBody Map<String, List<Integer>> request) {
        List<Integer> ids = request.get("ids");
        for (Integer id : ids) {
            studentStudyTypeService.delete(id);
        }
        return ResponseEntity.ok().build();
    }

    // Student Ministry Type endpoints
    @GetMapping("/student-ministry-type")
    public ResponseEntity<Map<String, Object>> getAllStudentMinistryTypes(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        return getLookupTableData(studentMinistryTypeService, page, size, search, sortBy, sortDir);
    }

    @GetMapping("/student-ministry-type/{id}")
    public ResponseEntity<StudentMinistryType> getStudentMinistryTypeById(@PathVariable Integer id) {
        return ResponseEntity.ok(studentMinistryTypeService.getById(id));
    }

    @PostMapping("/student-ministry-type")
    public ResponseEntity<StudentMinistryType> createStudentMinistryType(@Valid @RequestBody StudentMinistryType entity) {
        return ResponseEntity.ok(studentMinistryTypeService.create(entity));
    }

    @PutMapping("/student-ministry-type/{id}")
    public ResponseEntity<StudentMinistryType> updateStudentMinistryType(@PathVariable Integer id,
            @Valid @RequestBody StudentMinistryType entity) {
        return ResponseEntity.ok(studentMinistryTypeService.update(id, entity));
    }

    @DeleteMapping("/student-ministry-type/{id}")
    public ResponseEntity<Void> deleteStudentMinistryType(@PathVariable Integer id) {
        studentMinistryTypeService.delete(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/student-ministry-type/batch")
    public ResponseEntity<Void> deleteStudentMinistryTypes(@Valid @RequestBody Map<String, List<Integer>> request) {
        List<Integer> ids = request.get("ids");
        for (Integer id : ids) {
            studentMinistryTypeService.delete(id);
        }
        return ResponseEntity.ok().build();
    }

    // Study Country In endpoints
    @GetMapping("/study-country-in")
    public ResponseEntity<Map<String, Object>> getAllStudyCountriesIn(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        return getLookupTableData(studyCountryInService, page, size, search, sortBy, sortDir);
    }

    @GetMapping("/study-country-in/{id}")
    public ResponseEntity<StudyCountryIn> getStudyCountryInById(@PathVariable Integer id) {
        return ResponseEntity.ok(studyCountryInService.getById(id));
    }

    @PostMapping("/study-country-in")
    public ResponseEntity<StudyCountryIn> createStudyCountryIn(@Valid @RequestBody StudyCountryIn entity) {
        return ResponseEntity.ok(studyCountryInService.create(entity));
    }

    @PutMapping("/study-country-in/{id}")
    public ResponseEntity<StudyCountryIn> updateStudyCountryIn(@PathVariable Integer id,
            @Valid @RequestBody StudyCountryIn entity) {
        return ResponseEntity.ok(studyCountryInService.update(id, entity));
    }

    @DeleteMapping("/study-country-in/{id}")
    public ResponseEntity<Void> deleteStudyCountryIn(@PathVariable Integer id) {
        studyCountryInService.delete(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/study-country-in/batch")
    public ResponseEntity<Void> deleteStudyCountriesIn(@Valid @RequestBody Map<String, List<Integer>> request) {
        List<Integer> ids = request.get("ids");
        for (Integer id : ids) {
            studyCountryInService.delete(id);
        }
        return ResponseEntity.ok().build();
    }

    // Study Gov In endpoints
    @GetMapping("/study-gov-in")
    public ResponseEntity<Map<String, Object>> getAllStudyGovsIn(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        return getLookupTableData(studyGovInService, page, size, search, sortBy, sortDir);
    }

    @GetMapping("/study-gov-in/{id}")
    public ResponseEntity<StudyGovIn> getStudyGovInById(@PathVariable Integer id) {
        return ResponseEntity.ok(studyGovInService.getById(id));
    }

    @PostMapping("/study-gov-in")
    public ResponseEntity<StudyGovIn> createStudyGovIn(@Valid @RequestBody StudyGovIn entity) {
        return ResponseEntity.ok(studyGovInService.create(entity));
    }

    @PutMapping("/study-gov-in/{id}")
    public ResponseEntity<StudyGovIn> updateStudyGovIn(@PathVariable Integer id, @Valid @RequestBody StudyGovIn entity) {
        return ResponseEntity.ok(studyGovInService.update(id, entity));
    }

    @DeleteMapping("/study-gov-in/{id}")
    public ResponseEntity<Void> deleteStudyGovIn(@PathVariable Integer id) {
        studyGovInService.delete(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/study-gov-in/batch")
    public ResponseEntity<Void> deleteStudyGovsIn(@Valid @RequestBody Map<String, List<Integer>> request) {
        List<Integer> ids = request.get("ids");
        for (Integer id : ids) {
            studyGovInService.delete(id);
        }
        return ResponseEntity.ok().build();
    }

    // Study University In endpoints
    /**
     * Universities, optionally those of one study country and governorate (live QA M10:
     * the Add Student wizard offered Egypt's universities for a Jordan student, because it
     * could only fetch them all and fell back to all when a filter matched nothing). A row
     * with no country or no governorate (the "Not Specified" row) serves every one.
     */
    @GetMapping("/study-university-in")
    public ResponseEntity<Map<String, Object>> getAllStudyUniversitiesIn(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) Integer govId) {
        List<StudyUniversityIn> rows = studyUniversityInService.getAll().stream()
                .filter(u -> servesPlace(u.getStudyCountryInId(), countryId))
                .filter(u -> servesPlace(u.getStudyGovInId(), govId))
                .toList();
        return pageOrWhole(rows, page, size, search, sortBy, sortDir);
    }

    /** A row's place matches when no place is asked for, when it is the one asked for, or when the row names none. */
    private static boolean servesPlace(Integer rowPlace, Integer wanted) {
        return wanted == null || rowPlace == null || rowPlace == 0 || rowPlace.equals(wanted);
    }

    @GetMapping("/study-university-in/{id}")
    public ResponseEntity<StudyUniversityIn> getStudyUniversityInById(@PathVariable Integer id) {
        return ResponseEntity.ok(studyUniversityInService.getById(id));
    }

    @PostMapping("/study-university-in")
    public ResponseEntity<StudyUniversityIn> createStudyUniversityIn(@Valid @RequestBody StudyUniversityIn entity) {
        return ResponseEntity.ok(studyUniversityInService.create(entity));
    }

    @PutMapping("/study-university-in/{id}")
    public ResponseEntity<StudyUniversityIn> updateStudyUniversityIn(@PathVariable Integer id,
            @Valid @RequestBody StudyUniversityIn entity) {
        return ResponseEntity.ok(studyUniversityInService.update(id, entity));
    }

    @DeleteMapping("/study-university-in/{id}")
    public ResponseEntity<Void> deleteStudyUniversityIn(@PathVariable Integer id) {
        studyUniversityInService.delete(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/study-university-in/batch")
    public ResponseEntity<Void> deleteStudyUniversitiesIn(@Valid @RequestBody Map<String, List<Integer>> request) {
        List<Integer> ids = request.get("ids");
        for (Integer id : ids) {
            studyUniversityInService.delete(id);
        }
        return ResponseEntity.ok().build();
    }

    // Study College In endpoints
    @GetMapping("/study-college-in")
    public ResponseEntity<Map<String, Object>> getAllStudyCollegesIn(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        return getLookupTableData(studyCollegeInService, page, size, search, sortBy, sortDir);
    }

    @GetMapping("/study-college-in/{id}")
    public ResponseEntity<StudyCollegeIn> getStudyCollegeInById(@PathVariable Integer id) {
        return ResponseEntity.ok(studyCollegeInService.getById(id));
    }

    @PostMapping("/study-college-in")
    public ResponseEntity<StudyCollegeIn> createStudyCollegeIn(@Valid @RequestBody StudyCollegeIn entity) {
        return ResponseEntity.ok(studyCollegeInService.create(entity));
    }

    @PutMapping("/study-college-in/{id}")
    public ResponseEntity<StudyCollegeIn> updateStudyCollegeIn(@PathVariable Integer id,
            @Valid @RequestBody StudyCollegeIn entity) {
        return ResponseEntity.ok(studyCollegeInService.update(id, entity));
    }

    @DeleteMapping("/study-college-in/{id}")
    public ResponseEntity<Void> deleteStudyCollegeIn(@PathVariable Integer id) {
        studyCollegeInService.delete(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/study-college-in/batch")
    public ResponseEntity<Void> deleteStudyCollegesIn(@Valid @RequestBody Map<String, List<Integer>> request) {
        List<Integer> ids = request.get("ids");
        for (Integer id : ids) {
            studyCollegeInService.delete(id);
        }
        return ResponseEntity.ok().build();
    }

    // Study Education In endpoints
    @GetMapping("/study-education-in")
    public ResponseEntity<?> getAllStudyEducationsIn(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer collegeInId,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        if (collegeInId != null) {
            return ResponseEntity.ok(studyEductionInService.getByCollegeId(collegeInId));
        }
        return getLookupTableData(studyEductionInService, page, size, search, sortBy, sortDir);
    }

    @GetMapping("/study-education-in/{id}")
    public ResponseEntity<StudyEductionIn> getStudyEducationInById(@PathVariable Integer id) {
        return ResponseEntity.ok(studyEductionInService.getById(id));
    }

    @PostMapping("/study-education-in")
    public ResponseEntity<StudyEductionIn> createStudyEducationIn(@Valid @RequestBody StudyEductionIn entity) {
        return ResponseEntity.ok(studyEductionInService.create(entity));
    }

    @PutMapping("/study-education-in/{id}")
    public ResponseEntity<StudyEductionIn> updateStudyEducationIn(@PathVariable Integer id,
            @Valid @RequestBody StudyEductionIn entity) {
        return ResponseEntity.ok(studyEductionInService.update(id, entity));
    }

    @DeleteMapping("/study-education-in/{id}")
    public ResponseEntity<Void> deleteStudyEducationIn(@PathVariable Integer id) {
        studyEductionInService.delete(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/study-education-in/batch")
    public ResponseEntity<Void> deleteStudyEducationsIn(@Valid @RequestBody Map<String, List<Integer>> request) {
        List<Integer> ids = request.get("ids");
        for (Integer id : ids) {
            studyEductionInService.delete(id);
        }
        return ResponseEntity.ok().build();
    }

    // Study Major In endpoints
    @GetMapping("/study-major-in")
    public ResponseEntity<?> getAllStudyMajorsIn(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer educationId,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        if (educationId != null) {
            return ResponseEntity.ok(studyMajorInService.getByEducationId(educationId));
        }
        return getLookupTableData(studyMajorInService, page, size, search, sortBy, sortDir);
    }

    @GetMapping("/study-major-in/{id}")
    public ResponseEntity<StudyMajorIn> getStudyMajorInById(@PathVariable Integer id) {
        return ResponseEntity.ok(studyMajorInService.getById(id));
    }

    @PostMapping("/study-major-in")
    public ResponseEntity<StudyMajorIn> createStudyMajorIn(@Valid @RequestBody StudyMajorIn entity) {
        return ResponseEntity.ok(studyMajorInService.create(entity));
    }

    @PutMapping("/study-major-in/{id}")
    public ResponseEntity<StudyMajorIn> updateStudyMajorIn(@PathVariable Integer id, @Valid @RequestBody StudyMajorIn entity) {
        return ResponseEntity.ok(studyMajorInService.update(id, entity));
    }

    @DeleteMapping("/study-major-in/{id}")
    public ResponseEntity<Void> deleteStudyMajorIn(@PathVariable Integer id) {
        studyMajorInService.delete(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/study-major-in/batch")
    public ResponseEntity<Void> deleteStudyMajorsIn(@Valid @RequestBody Map<String, List<Integer>> request) {
        List<Integer> ids = request.get("ids");
        for (Integer id : ids) {
            studyMajorInService.delete(id);
        }
        return ResponseEntity.ok().build();
    }

    // Student Study Status endpoints
    @GetMapping("/student-study-status")
    public ResponseEntity<Map<String, Object>> getAllStudentStudyStatuses(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        return getLookupTableData(studentStudyStatusService, page, size, search, sortBy, sortDir);
    }

    @GetMapping("/student-study-status/{id}")
    public ResponseEntity<StudentStudyStatus> getStudentStudyStatusById(@PathVariable Integer id) {
        return ResponseEntity.ok(studentStudyStatusService.getById(id));
    }

    @PostMapping("/student-study-status")
    public ResponseEntity<StudentStudyStatus> createStudentStudyStatus(@Valid @RequestBody StudentStudyStatus entity) {
        return ResponseEntity.ok(studentStudyStatusService.create(entity));
    }

    @PutMapping("/student-study-status/{id}")
    public ResponseEntity<StudentStudyStatus> updateStudentStudyStatus(@PathVariable Integer id,
            @Valid @RequestBody StudentStudyStatus entity) {
        return ResponseEntity.ok(studentStudyStatusService.update(id, entity));
    }

    @DeleteMapping("/student-study-status/{id}")
    public ResponseEntity<Void> deleteStudentStudyStatus(@PathVariable Integer id) {
        studentStudyStatusService.delete(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/student-study-status/batch")
    public ResponseEntity<Void> deleteStudentStudyStatuses(@Valid @RequestBody Map<String, List<Integer>> request) {
        List<Integer> ids = request.get("ids");
        for (Integer id : ids) {
            studentStudyStatusService.delete(id);
        }
        return ResponseEntity.ok().build();
    }

    // ── Wafar Spending Status ──────────────────────────────────────────────────
    @GetMapping("/wafar-spending-statuses")
    public ResponseEntity<?> getWafarSpendingStatuses(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        return listOrPage(luWafarSpendingStatusService.getAll(), page, size, search, sortBy, sortDir);
    }

    @PostMapping("/wafar-spending-statuses")
    public ResponseEntity<LuWafarSpendingStatus> createWafarSpendingStatus(@Valid @RequestBody LuWafarSpendingStatus entity) {
        return ResponseEntity.ok(luWafarSpendingStatusService.create(entity));
    }

    @PutMapping("/wafar-spending-statuses/{id}")
    public ResponseEntity<LuWafarSpendingStatus> updateWafarSpendingStatus(@PathVariable Integer id, @Valid @RequestBody LuWafarSpendingStatus entity) {
        return ResponseEntity.ok(luWafarSpendingStatusService.update(id, entity));
    }

    /**
     * Read by id, for each of the bilingual lists. The Lookup Tables screen's row
     * dialog asked for it and Spring answered 405, since only the list, POST, PUT
     * and DELETE were mapped (live test L9, 23 Sep 2026).
     */
    @GetMapping("/wafar-spending-statuses/{id}")
    public ResponseEntity<LuWafarSpendingStatus> getWafarSpendingStatusById(@PathVariable Integer id) {
        return ResponseEntity.ok(luWafarSpendingStatusService.getById(id));
    }

    /**
     * Delete selected, for the bilingual lists.
     *
     * <p>The Lookup Tables screen has always offered it and it has never worked
     * here: it calls {@code DELETE .../{table}/batch}, no such route existed, so
     * {@code /{id}} matched with an id of "batch" and the request came back 400.
     * Each of these refuses a system row exactly as the single delete does, so
     * selecting a whole page and pressing the button cannot do what one click
     * could not.
     */
    @DeleteMapping("/wafar-spending-statuses/batch")
    public ResponseEntity<Void> deleteWafarSpendingStatusBatch(@Valid @RequestBody Map<String, List<Integer>> request) {
        for (Integer id : request.getOrDefault("ids", List.of())) {
            luWafarSpendingStatusService.delete(id);
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/wafar-spending-statuses/{id}")
    public ResponseEntity<Void> deleteWafarSpendingStatus(@PathVariable Integer id) {
        luWafarSpendingStatusService.delete(id);
        return ResponseEntity.ok().build();
    }

    // ── Wafar Reconciliation Status ───────────────────────────────────────────
    @GetMapping("/wafar-reconciliation-statuses")
    public ResponseEntity<?> getWafarReconciliationStatuses(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        return listOrPage(luWafarReconciliationStatusService.getAll(), page, size, search, sortBy, sortDir);
    }

    @PostMapping("/wafar-reconciliation-statuses")
    public ResponseEntity<LuWafarReconciliationStatus> createWafarReconciliationStatus(@Valid @RequestBody LuWafarReconciliationStatus entity) {
        return ResponseEntity.ok(luWafarReconciliationStatusService.create(entity));
    }

    @PutMapping("/wafar-reconciliation-statuses/{id}")
    public ResponseEntity<LuWafarReconciliationStatus> updateWafarReconciliationStatus(@PathVariable Integer id, @Valid @RequestBody LuWafarReconciliationStatus entity) {
        return ResponseEntity.ok(luWafarReconciliationStatusService.update(id, entity));
    }

    @GetMapping("/wafar-reconciliation-statuses/{id}")
    public ResponseEntity<LuWafarReconciliationStatus> getWafarReconciliationStatusById(@PathVariable Integer id) {
        return ResponseEntity.ok(luWafarReconciliationStatusService.getById(id));
    }

    @DeleteMapping("/wafar-reconciliation-statuses/batch")
    public ResponseEntity<Void> deleteWafarReconciliationStatusBatch(@Valid @RequestBody Map<String, List<Integer>> request) {
        for (Integer id : request.getOrDefault("ids", List.of())) {
            luWafarReconciliationStatusService.delete(id);
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/wafar-reconciliation-statuses/{id}")
    public ResponseEntity<Void> deleteWafarReconciliationStatus(@PathVariable Integer id) {
        luWafarReconciliationStatusService.delete(id);
        return ResponseEntity.ok().build();
    }

    // ── Payment Auth Status ───────────────────────────────────────────────────
    @GetMapping("/payment-auth-statuses")
    public ResponseEntity<?> getPaymentAuthStatuses(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        return listOrPage(luPaymentAuthStatusService.getAll(), page, size, search, sortBy, sortDir);
    }

    @PostMapping("/payment-auth-statuses")
    public ResponseEntity<LuPaymentAuthStatus> createPaymentAuthStatus(@Valid @RequestBody LuPaymentAuthStatus entity) {
        return ResponseEntity.ok(luPaymentAuthStatusService.create(entity));
    }

    @PutMapping("/payment-auth-statuses/{id}")
    public ResponseEntity<LuPaymentAuthStatus> updatePaymentAuthStatus(@PathVariable Integer id, @Valid @RequestBody LuPaymentAuthStatus entity) {
        return ResponseEntity.ok(luPaymentAuthStatusService.update(id, entity));
    }

    @GetMapping("/payment-auth-statuses/{id}")
    public ResponseEntity<LuPaymentAuthStatus> getPaymentAuthStatusById(@PathVariable Integer id) {
        return ResponseEntity.ok(luPaymentAuthStatusService.getById(id));
    }

    @DeleteMapping("/payment-auth-statuses/batch")
    public ResponseEntity<Void> deletePaymentAuthStatusBatch(@Valid @RequestBody Map<String, List<Integer>> request) {
        for (Integer id : request.getOrDefault("ids", List.of())) {
            luPaymentAuthStatusService.delete(id);
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/payment-auth-statuses/{id}")
    public ResponseEntity<Void> deletePaymentAuthStatus(@PathVariable Integer id) {
        luPaymentAuthStatusService.delete(id);
        return ResponseEntity.ok().build();
    }

    // ── Payment Amount Source ─────────────────────────────────────────────────
    @GetMapping("/payment-amount-sources")
    public ResponseEntity<?> getPaymentAmountSources(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        return listOrPage(luPaymentAmountSourceService.getAll(), page, size, search, sortBy, sortDir);
    }

    @PostMapping("/payment-amount-sources")
    public ResponseEntity<LuPaymentAmountSource> createPaymentAmountSource(@Valid @RequestBody LuPaymentAmountSource entity) {
        return ResponseEntity.ok(luPaymentAmountSourceService.create(entity));
    }

    @PutMapping("/payment-amount-sources/{id}")
    public ResponseEntity<LuPaymentAmountSource> updatePaymentAmountSource(@PathVariable Integer id, @Valid @RequestBody LuPaymentAmountSource entity) {
        return ResponseEntity.ok(luPaymentAmountSourceService.update(id, entity));
    }

    @GetMapping("/payment-amount-sources/{id}")
    public ResponseEntity<LuPaymentAmountSource> getPaymentAmountSourceById(@PathVariable Integer id) {
        return ResponseEntity.ok(luPaymentAmountSourceService.getById(id));
    }

    @DeleteMapping("/payment-amount-sources/batch")
    public ResponseEntity<Void> deletePaymentAmountSourceBatch(@Valid @RequestBody Map<String, List<Integer>> request) {
        for (Integer id : request.getOrDefault("ids", List.of())) {
            luPaymentAmountSourceService.delete(id);
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/payment-amount-sources/{id}")
    public ResponseEntity<Void> deletePaymentAmountSource(@PathVariable Integer id) {
        luPaymentAmountSourceService.delete(id);
        return ResponseEntity.ok().build();
    }

    // ── Change Request Status ─────────────────────────────────────────────────
    @GetMapping("/change-request-statuses")
    public ResponseEntity<?> getChangeRequestStatuses(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        return listOrPage(luChangeRequestStatusService.getAll(), page, size, search, sortBy, sortDir);
    }

    @PostMapping("/change-request-statuses")
    public ResponseEntity<LuChangeRequestStatus> createChangeRequestStatus(@Valid @RequestBody LuChangeRequestStatus entity) {
        return ResponseEntity.ok(luChangeRequestStatusService.create(entity));
    }

    @PutMapping("/change-request-statuses/{id}")
    public ResponseEntity<LuChangeRequestStatus> updateChangeRequestStatus(@PathVariable Integer id, @Valid @RequestBody LuChangeRequestStatus entity) {
        return ResponseEntity.ok(luChangeRequestStatusService.update(id, entity));
    }

    @GetMapping("/change-request-statuses/{id}")
    public ResponseEntity<LuChangeRequestStatus> getChangeRequestStatusById(@PathVariable Integer id) {
        return ResponseEntity.ok(luChangeRequestStatusService.getById(id));
    }

    @DeleteMapping("/change-request-statuses/batch")
    public ResponseEntity<Void> deleteChangeRequestStatusBatch(@Valid @RequestBody Map<String, List<Integer>> request) {
        for (Integer id : request.getOrDefault("ids", List.of())) {
            luChangeRequestStatusService.delete(id);
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/change-request-statuses/{id}")
    public ResponseEntity<Void> deleteChangeRequestStatus(@PathVariable Integer id) {
        luChangeRequestStatusService.delete(id);
        return ResponseEntity.ok().build();
    }

    // ── Student Portal Account Status ─────────────────────────────────────────
    @GetMapping("/student-portal-account-statuses")
    public ResponseEntity<?> getStudentPortalAccountStatuses(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        return listOrPage(luStudentPortalAccountStatusService.getAll(), page, size, search, sortBy, sortDir);
    }

    @PostMapping("/student-portal-account-statuses")
    public ResponseEntity<LuStudentPortalAccountStatus> createStudentPortalAccountStatus(@Valid @RequestBody LuStudentPortalAccountStatus entity) {
        return ResponseEntity.ok(luStudentPortalAccountStatusService.create(entity));
    }

    @PutMapping("/student-portal-account-statuses/{id}")
    public ResponseEntity<LuStudentPortalAccountStatus> updateStudentPortalAccountStatus(@PathVariable Integer id, @Valid @RequestBody LuStudentPortalAccountStatus entity) {
        return ResponseEntity.ok(luStudentPortalAccountStatusService.update(id, entity));
    }

    @GetMapping("/student-portal-account-statuses/{id}")
    public ResponseEntity<LuStudentPortalAccountStatus> getStudentPortalAccountStatusById(@PathVariable Integer id) {
        return ResponseEntity.ok(luStudentPortalAccountStatusService.getById(id));
    }

    @DeleteMapping("/student-portal-account-statuses/batch")
    public ResponseEntity<Void> deleteStudentPortalAccountStatusBatch(@Valid @RequestBody Map<String, List<Integer>> request) {
        for (Integer id : request.getOrDefault("ids", List.of())) {
            luStudentPortalAccountStatusService.delete(id);
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/student-portal-account-statuses/{id}")
    public ResponseEntity<Void> deleteStudentPortalAccountStatus(@PathVariable Integer id) {
        luStudentPortalAccountStatusService.delete(id);
        return ResponseEntity.ok().build();
    }

    // ── Notification Channel ──────────────────────────────────────────────────
    @GetMapping("/notification-channels")
    public ResponseEntity<?> getNotificationChannels(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        return listOrPage(luNotificationChannelService.getAll(), page, size, search, sortBy, sortDir);
    }

    @PostMapping("/notification-channels")
    public ResponseEntity<LuNotificationChannel> createNotificationChannel(@Valid @RequestBody LuNotificationChannel entity) {
        return ResponseEntity.ok(luNotificationChannelService.create(entity));
    }

    @PutMapping("/notification-channels/{id}")
    public ResponseEntity<LuNotificationChannel> updateNotificationChannel(@PathVariable Integer id, @Valid @RequestBody LuNotificationChannel entity) {
        return ResponseEntity.ok(luNotificationChannelService.update(id, entity));
    }

    @GetMapping("/notification-channels/{id}")
    public ResponseEntity<LuNotificationChannel> getNotificationChannelById(@PathVariable Integer id) {
        return ResponseEntity.ok(luNotificationChannelService.getById(id));
    }

    @DeleteMapping("/notification-channels/batch")
    public ResponseEntity<Void> deleteNotificationChannelBatch(@Valid @RequestBody Map<String, List<Integer>> request) {
        for (Integer id : request.getOrDefault("ids", List.of())) {
            luNotificationChannelService.delete(id);
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/notification-channels/{id}")
    public ResponseEntity<Void> deleteNotificationChannel(@PathVariable Integer id) {
        luNotificationChannelService.delete(id);
        return ResponseEntity.ok().build();
    }

    // ── Notification Recipient Type ───────────────────────────────────────────
    @GetMapping("/notification-recipient-types")
    public ResponseEntity<?> getNotificationRecipientTypes(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        return listOrPage(luNotificationRecipientTypeService.getAll(), page, size, search, sortBy, sortDir);
    }

    @PostMapping("/notification-recipient-types")
    public ResponseEntity<LuNotificationRecipientType> createNotificationRecipientType(@Valid @RequestBody LuNotificationRecipientType entity) {
        return ResponseEntity.ok(luNotificationRecipientTypeService.create(entity));
    }

    @PutMapping("/notification-recipient-types/{id}")
    public ResponseEntity<LuNotificationRecipientType> updateNotificationRecipientType(@PathVariable Integer id, @Valid @RequestBody LuNotificationRecipientType entity) {
        return ResponseEntity.ok(luNotificationRecipientTypeService.update(id, entity));
    }

    @GetMapping("/notification-recipient-types/{id}")
    public ResponseEntity<LuNotificationRecipientType> getNotificationRecipientTypeById(@PathVariable Integer id) {
        return ResponseEntity.ok(luNotificationRecipientTypeService.getById(id));
    }

    @DeleteMapping("/notification-recipient-types/batch")
    public ResponseEntity<Void> deleteNotificationRecipientTypeBatch(@Valid @RequestBody Map<String, List<Integer>> request) {
        for (Integer id : request.getOrDefault("ids", List.of())) {
            luNotificationRecipientTypeService.delete(id);
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/notification-recipient-types/{id}")
    public ResponseEntity<Void> deleteNotificationRecipientType(@PathVariable Integer id) {
        luNotificationRecipientTypeService.delete(id);
        return ResponseEntity.ok().build();
    }

    // ── Notification Scope ────────────────────────────────────────────────────
    @GetMapping("/notification-scopes")
    public ResponseEntity<?> getNotificationScopes(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        return listOrPage(luNotificationScopeService.getAll(), page, size, search, sortBy, sortDir);
    }

    @PostMapping("/notification-scopes")
    public ResponseEntity<LuNotificationScope> createNotificationScope(@Valid @RequestBody LuNotificationScope entity) {
        return ResponseEntity.ok(luNotificationScopeService.create(entity));
    }

    @PutMapping("/notification-scopes/{id}")
    public ResponseEntity<LuNotificationScope> updateNotificationScope(@PathVariable Integer id, @Valid @RequestBody LuNotificationScope entity) {
        return ResponseEntity.ok(luNotificationScopeService.update(id, entity));
    }

    @GetMapping("/notification-scopes/{id}")
    public ResponseEntity<LuNotificationScope> getNotificationScopeById(@PathVariable Integer id) {
        return ResponseEntity.ok(luNotificationScopeService.getById(id));
    }

    @DeleteMapping("/notification-scopes/batch")
    public ResponseEntity<Void> deleteNotificationScopeBatch(@Valid @RequestBody Map<String, List<Integer>> request) {
        for (Integer id : request.getOrDefault("ids", List.of())) {
            luNotificationScopeService.delete(id);
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/notification-scopes/{id}")
    public ResponseEntity<Void> deleteNotificationScope(@PathVariable Integer id) {
        luNotificationScopeService.delete(id);
        return ResponseEntity.ok().build();
    }

    // ── Quarter Number ────────────────────────────────────────────────────────
    @GetMapping("/quarter-numbers")
    public ResponseEntity<?> getQuarterNumbers(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        return listOrPage(luQuarterNumberService.getAll(), page, size, search, sortBy, sortDir);
    }

    @PostMapping("/quarter-numbers")
    public ResponseEntity<LuQuarterNumber> createQuarterNumber(@Valid @RequestBody LuQuarterNumber entity) {
        return ResponseEntity.ok(luQuarterNumberService.create(entity));
    }

    @PutMapping("/quarter-numbers/{id}")
    public ResponseEntity<LuQuarterNumber> updateQuarterNumber(@PathVariable Integer id, @Valid @RequestBody LuQuarterNumber entity) {
        return ResponseEntity.ok(luQuarterNumberService.update(id, entity));
    }

    @GetMapping("/quarter-numbers/{id}")
    public ResponseEntity<LuQuarterNumber> getQuarterNumberById(@PathVariable Integer id) {
        return ResponseEntity.ok(luQuarterNumberService.getById(id));
    }

    @DeleteMapping("/quarter-numbers/batch")
    public ResponseEntity<Void> deleteQuarterNumberBatch(@Valid @RequestBody Map<String, List<Integer>> request) {
        for (Integer id : request.getOrDefault("ids", List.of())) {
            luQuarterNumberService.delete(id);
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/quarter-numbers/{id}")
    public ResponseEntity<Void> deleteQuarterNumber(@PathVariable Integer id) {
        luQuarterNumberService.delete(id);
        return ResponseEntity.ok().build();
    }

    // ── Country All (all world countries — used for birth place & passport issue) ─
    @GetMapping("/country-all")
    public ResponseEntity<List<StudentCountryAll>> getAllCountriesAll() {
        return ResponseEntity.ok(studentCountryAllService.getAll());
    }

    @GetMapping("/country-all/{id}")
    public ResponseEntity<StudentCountryAll> getCountryAllById(@PathVariable Integer id) {
        return ResponseEntity.ok(studentCountryAllService.getById(id));
    }

    @PostMapping("/country-all")
    public ResponseEntity<StudentCountryAll> createCountryAll(@Valid @RequestBody StudentCountryAll entity) {
        return ResponseEntity.ok(studentCountryAllService.create(entity));
    }

    @PutMapping("/country-all/{id}")
    public ResponseEntity<StudentCountryAll> updateCountryAll(@PathVariable Integer id,
            @Valid @RequestBody StudentCountryAll entity) {
        return ResponseEntity.ok(studentCountryAllService.update(id, entity));
    }

    @DeleteMapping("/country-all/{id}")
    public ResponseEntity<Void> deleteCountryAll(@PathVariable Integer id) {
        studentCountryAllService.delete(id);
        return ResponseEntity.ok().build();
    }

    // ── Business lookup tables (fees, alerts, yes/no, years) ───────────────────

    @GetMapping("/university-fee-statuses")
    public ResponseEntity<List<LuUniversityFeeStatus>> getUniversityFeeStatuses() {
        return ResponseEntity.ok(businessLookupService.getUniversityFeeStatuses());
    }

    @GetMapping("/fee-sources")
    public ResponseEntity<List<LuFeeSource>> getFeeSources() {
        return ResponseEntity.ok(businessLookupService.getFeeSources());
    }

    @GetMapping("/fee-check-payors")
    public ResponseEntity<List<LuFeeCheckPayor>> getFeeCheckPayors() {
        return ResponseEntity.ok(businessLookupService.getFeeCheckPayors());
    }

    @GetMapping("/fee-scopes")
    public ResponseEntity<List<LuFeeScope>> getFeeScopes(
            @RequestParam(required = false) String scopeType) {
        return ResponseEntity.ok(businessLookupService.getFeeScopes(scopeType));
    }

    @GetMapping("/alert-statuses")
    public ResponseEntity<List<LuAlertStatus>> getAlertStatuses() {
        return ResponseEntity.ok(businessLookupService.getAlertStatuses());
    }

    @GetMapping("/alert-severities")
    public ResponseEntity<List<LuAlertSeverity>> getAlertSeverities() {
        return ResponseEntity.ok(businessLookupService.getAlertSeverities());
    }

    @GetMapping("/yes-no")
    public ResponseEntity<List<LuYesNo>> getYesNoOptions() {
        return ResponseEntity.ok(businessLookupService.getYesNoOptions());
    }

    @GetMapping("/status-active")
    public ResponseEntity<List<LuStatusActive>> getStatusActiveOptions() {
        return ResponseEntity.ok(businessLookupService.getStatusActiveOptions());
    }

    @GetMapping("/academic-years")
    public ResponseEntity<List<LuAcademicYear>> getAcademicYears() {
        return ResponseEntity.ok(businessLookupService.getAcademicYears());
    }

    @GetMapping("/semester-numbers")
    public ResponseEntity<List<LuSemesterNumber>> getSemesterNumbers() {
        return ResponseEntity.ok(businessLookupService.getSemesterNumbers());
    }

    @GetMapping("/books-allowance-pay-statuses")
    public ResponseEntity<?> getBooksAllowancePayStatuses(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        return listOrPage(luExtendedLookupAdminService.getAllBooksAllowancePayStatuses(), page, size, search, sortBy, sortDir);
    }

    @PostMapping("/books-allowance-pay-statuses")
    public ResponseEntity<LuBooksAllowancePayStatus> createBooksAllowancePayStatus(
            @Valid @RequestBody LuBooksAllowancePayStatus entity) {
        return ResponseEntity.ok(luExtendedLookupAdminService.createBooksAllowancePayStatus(entity));
    }

    @PutMapping("/books-allowance-pay-statuses/{id}")
    public ResponseEntity<LuBooksAllowancePayStatus> updateBooksAllowancePayStatus(
            @PathVariable Integer id, @Valid @RequestBody LuBooksAllowancePayStatus entity) {
        return ResponseEntity.ok(luExtendedLookupAdminService.updateBooksAllowancePayStatus(id, entity));
    }

    @GetMapping("/books-allowance-pay-statuses/{id}")
    public ResponseEntity<LuBooksAllowancePayStatus> getBooksAllowancePayStatusById(@PathVariable Integer id) {
        return ResponseEntity.ok(luExtendedLookupAdminService.getBooksAllowancePayStatusById(id));
    }

    @DeleteMapping("/books-allowance-pay-statuses/batch")
    public ResponseEntity<Void> deleteBooksAllowancePayStatusBatch(@Valid @RequestBody Map<String, List<Integer>> request) {
        for (Integer id : request.getOrDefault("ids", List.of())) {
            luExtendedLookupAdminService.deleteBooksAllowancePayStatus(id);
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/books-allowance-pay-statuses/{id}")
    public ResponseEntity<Void> deleteBooksAllowancePayStatus(@PathVariable Integer id) {
        luExtendedLookupAdminService.deleteBooksAllowancePayStatus(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/loan-statuses")
    public ResponseEntity<?> getLoanStatuses(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        return listOrPage(luExtendedLookupAdminService.getAllLoanStatuses(), page, size, search, sortBy, sortDir);
    }

    @PostMapping("/loan-statuses")
    public ResponseEntity<LuLoanStatus> createLoanStatus(@Valid @RequestBody LuLoanStatus entity) {
        return ResponseEntity.ok(luExtendedLookupAdminService.createLoanStatus(entity));
    }

    @PutMapping("/loan-statuses/{id}")
    public ResponseEntity<LuLoanStatus> updateLoanStatus(
            @PathVariable Integer id, @Valid @RequestBody LuLoanStatus entity) {
        return ResponseEntity.ok(luExtendedLookupAdminService.updateLoanStatus(id, entity));
    }

    @GetMapping("/loan-statuses/{id}")
    public ResponseEntity<LuLoanStatus> getLoanStatusById(@PathVariable Integer id) {
        return ResponseEntity.ok(luExtendedLookupAdminService.getLoanStatusById(id));
    }

    @DeleteMapping("/loan-statuses/batch")
    public ResponseEntity<Void> deleteLoanStatusBatch(@Valid @RequestBody Map<String, List<Integer>> request) {
        for (Integer id : request.getOrDefault("ids", List.of())) {
            luExtendedLookupAdminService.deleteLoanStatus(id);
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/loan-statuses/{id}")
    public ResponseEntity<Void> deleteLoanStatus(@PathVariable Integer id) {
        luExtendedLookupAdminService.deleteLoanStatus(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/report-dimensions")
    public ResponseEntity<?> getReportDimensions(
            @RequestParam(required = false) String groupCode,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        if (groupCode == null || groupCode.isBlank()) {
            return listOrPage(luExtendedLookupAdminService.getAllReportDimensions(), page, size, search, sortBy, sortDir);
        }
        return ResponseEntity.ok(businessLookupService.getReportDimensions(groupCode));
    }

    @PostMapping("/report-dimensions")
    public ResponseEntity<LuReportDimension> createReportDimension(@Valid @RequestBody LuReportDimension entity) {
        return ResponseEntity.ok(luExtendedLookupAdminService.createReportDimension(entity));
    }

    @PutMapping("/report-dimensions/{id}")
    public ResponseEntity<LuReportDimension> updateReportDimension(
            @PathVariable Integer id, @Valid @RequestBody LuReportDimension entity) {
        return ResponseEntity.ok(luExtendedLookupAdminService.updateReportDimension(id, entity));
    }

    @GetMapping("/report-dimensions/{id}")
    public ResponseEntity<LuReportDimension> getReportDimensionById(@PathVariable Integer id) {
        return ResponseEntity.ok(luExtendedLookupAdminService.getReportDimensionById(id));
    }

    @DeleteMapping("/report-dimensions/batch")
    public ResponseEntity<Void> deleteReportDimensionBatch(@Valid @RequestBody Map<String, List<Integer>> request) {
        for (Integer id : request.getOrDefault("ids", List.of())) {
            luExtendedLookupAdminService.deleteReportDimension(id);
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/report-dimensions/{id}")
    public ResponseEntity<Void> deleteReportDimension(@PathVariable Integer id) {
        luExtendedLookupAdminService.deleteReportDimension(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/trend-classes")
    public ResponseEntity<?> getTrendClasses(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        if (category == null || category.isBlank()) {
            return listOrPage(luExtendedLookupAdminService.getAllTrendClasses(), page, size, search, sortBy, sortDir);
        }
        return ResponseEntity.ok(businessLookupService.getTrendClasses(category));
    }

    @PostMapping("/trend-classes")
    public ResponseEntity<LuTrendClass> createTrendClass(@Valid @RequestBody LuTrendClass entity) {
        return ResponseEntity.ok(luExtendedLookupAdminService.createTrendClass(entity));
    }

    @PutMapping("/trend-classes/{id}")
    public ResponseEntity<LuTrendClass> updateTrendClass(
            @PathVariable Integer id, @Valid @RequestBody LuTrendClass entity) {
        return ResponseEntity.ok(luExtendedLookupAdminService.updateTrendClass(id, entity));
    }

    @GetMapping("/trend-classes/{id}")
    public ResponseEntity<LuTrendClass> getTrendClassById(@PathVariable Integer id) {
        return ResponseEntity.ok(luExtendedLookupAdminService.getTrendClassById(id));
    }

    @DeleteMapping("/trend-classes/batch")
    public ResponseEntity<Void> deleteTrendClassBatch(@Valid @RequestBody Map<String, List<Integer>> request) {
        for (Integer id : request.getOrDefault("ids", List.of())) {
            luExtendedLookupAdminService.deleteTrendClass(id);
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/trend-classes/{id}")
    public ResponseEntity<Void> deleteTrendClass(@PathVariable Integer id) {
        luExtendedLookupAdminService.deleteTrendClass(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/report-labels")
    public ResponseEntity<?> getReportLabels(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        return listOrPage(luExtendedLookupAdminService.getAllReportLabels(), page, size, search, sortBy, sortDir);
    }

    @PostMapping("/report-labels")
    public ResponseEntity<LuReportLabel> createReportLabel(@Valid @RequestBody LuReportLabel entity) {
        return ResponseEntity.ok(luExtendedLookupAdminService.createReportLabel(entity));
    }

    @PutMapping("/report-labels/{id}")
    public ResponseEntity<LuReportLabel> updateReportLabel(
            @PathVariable Integer id, @Valid @RequestBody LuReportLabel entity) {
        return ResponseEntity.ok(luExtendedLookupAdminService.updateReportLabel(id, entity));
    }

    @GetMapping("/report-labels/{id}")
    public ResponseEntity<LuReportLabel> getReportLabelById(@PathVariable Integer id) {
        return ResponseEntity.ok(luExtendedLookupAdminService.getReportLabelById(id));
    }

    @DeleteMapping("/report-labels/batch")
    public ResponseEntity<Void> deleteReportLabelBatch(@Valid @RequestBody Map<String, List<Integer>> request) {
        for (Integer id : request.getOrDefault("ids", List.of())) {
            luExtendedLookupAdminService.deleteReportLabel(id);
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/report-labels/{id}")
    public ResponseEntity<Void> deleteReportLabel(@PathVariable Integer id) {
        luExtendedLookupAdminService.deleteReportLabel(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/death-procedures")
    public ResponseEntity<?> getDeathProcedures(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        return listOrPage(luExtendedLookupAdminService.getAllDeathProcedures(), page, size, search, sortBy, sortDir);
    }

    @PostMapping("/death-procedures")
    public ResponseEntity<LuDeathProcedure> createDeathProcedure(@Valid @RequestBody LuDeathProcedure entity) {
        return ResponseEntity.ok(luExtendedLookupAdminService.createDeathProcedure(entity));
    }

    @PutMapping("/death-procedures/{id}")
    public ResponseEntity<LuDeathProcedure> updateDeathProcedure(
            @PathVariable Integer id, @Valid @RequestBody LuDeathProcedure entity) {
        return ResponseEntity.ok(luExtendedLookupAdminService.updateDeathProcedure(id, entity));
    }

    @GetMapping("/death-procedures/{id}")
    public ResponseEntity<LuDeathProcedure> getDeathProcedureById(@PathVariable Integer id) {
        return ResponseEntity.ok(luExtendedLookupAdminService.getDeathProcedureById(id));
    }

    @DeleteMapping("/death-procedures/batch")
    public ResponseEntity<Void> deleteDeathProcedureBatch(@Valid @RequestBody Map<String, List<Integer>> request) {
        for (Integer id : request.getOrDefault("ids", List.of())) {
            luExtendedLookupAdminService.deleteDeathProcedure(id);
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/death-procedures/{id}")
    public ResponseEntity<Void> deleteDeathProcedure(@PathVariable Integer id) {
        luExtendedLookupAdminService.deleteDeathProcedure(id);
        return ResponseEntity.ok().build();
    }
}