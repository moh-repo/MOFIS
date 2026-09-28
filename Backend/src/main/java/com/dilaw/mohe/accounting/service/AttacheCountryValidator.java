package com.dilaw.mohe.accounting.service;

import com.dilaw.mohe.model.StudyCountryIn;
import com.dilaw.mohe.repository.StudyCountryInRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * The attaché on an accounting record is a STUDY_COUNTRY_IN row (live QA H6).
 *
 * <p>The accounting screens offered the home-country list (STUDENT_COUNTRY), and
 * the two tables reuse the same ids for different countries, so a pick was
 * stored silently under another attaché. The screens now offer the study
 * countries; this check refuses an id that is not an active study country, so
 * a caller that still sends a home-country id is told instead of saved.
 */
@Component
public class AttacheCountryValidator {

    @Autowired private StudyCountryInRepository studyCountries;

    /** Null passes: "ministry-wide" where the record allows it is the caller's rule to enforce. */
    public void requireActive(Integer studyCountryInId) {
        if (studyCountryInId == null) return;
        boolean active = studyCountries.findById(studyCountryInId)
                .map(StudyCountryIn::getIsActive)
                .map(Boolean.TRUE::equals)
                .orElse(false);
        if (!active) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Attaché " + studyCountryInId + " is not an active study country (studyCountryInId).");
        }
    }
}
