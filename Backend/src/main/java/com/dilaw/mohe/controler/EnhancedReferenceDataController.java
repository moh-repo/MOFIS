package com.dilaw.mohe.controler;

import com.dilaw.mohe.repository.FinReceivedReasonRepository;
import com.dilaw.mohe.repository.LuFamilyRelationshipRepository;
import com.dilaw.mohe.repository.LuTravelTypeRepository;
import com.dilaw.mohe.repository.StudentInfoRequestKindRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import com.dilaw.mohe.config.MoheSecurityExpressions;

/**
 * Reference lists: relationships, travel types, reasons, request kinds.
 *
 * <p>Nothing here is anything but a GET of a small catalogue, and the screens
 * that need them are ordinary working screens — the finance recipients form asks
 * for family relationships and received reasons. Gated to system admin, those
 * calls answered 403 and the dropdowns rendered empty for exactly the people who
 * fill the form in. {@link MoheSecurityExpressions#LOOKUP_TABLE} is the gate the
 * lookup tables already use: GET is reference data, writes stay with admin —
 * and there are no writes here.
 */
@PreAuthorize(MoheSecurityExpressions.LOOKUP_TABLE)
@RestController
@RequestMapping("api/v1/ref-data")
public class EnhancedReferenceDataController {

    @Autowired
    private LuTravelTypeRepository luTravelTypeRepository;

    @Autowired
    private LuFamilyRelationshipRepository luFamilyRelationshipRepository;

    @Autowired
    private FinReceivedReasonRepository finReceivedReasonRepository;

    @Autowired
    private StudentInfoRequestKindRepository studentInfoRequestKindRepository;

    @GetMapping("/travel-types")
    public Object travelTypes() {
        return luTravelTypeRepository.findAllByActiveTrueOrderBySortOrderAsc();
    }

    @GetMapping("/family-relationships")
    public Object relationships() {
        return luFamilyRelationshipRepository.findAllByOrderBySortOrderAsc();
    }

    @GetMapping("/fin-received-reasons")
    public Object finReasons() {
        return finReceivedReasonRepository.findAllByActiveTrueOrderBySortOrderAsc();
    }

    @GetMapping("/student-info-request-kinds")
    public Object infoRequestKinds() {
        return studentInfoRequestKindRepository.findAllByOrderBySortOrderAsc();
    }
}
