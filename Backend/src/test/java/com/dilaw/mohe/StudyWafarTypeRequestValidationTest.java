package com.dilaw.mohe;

import com.dilaw.mohe.dto.StudyWafarTypeRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudyWafarTypeRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void blankWafarName_failsValidation() {
        StudyWafarTypeRequest request = new StudyWafarTypeRequest();
        request.setWafarName("   ");
        Set<ConstraintViolation<StudyWafarTypeRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    void validRequest_passesValidation() {
        StudyWafarTypeRequest request = new StudyWafarTypeRequest();
        request.setWafarName("Type A");
        Set<ConstraintViolation<StudyWafarTypeRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }
}
