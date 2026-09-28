package com.dilaw.mohe;

import com.dilaw.mohe.dto.StudentListDetailedDto;
import com.dilaw.mohe.dto.StudentListDetailedMapper;
import com.dilaw.mohe.model.StudentListDetailed;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StudentListDetailedMapperTest {

    @Test
    void toDto_mapsScalarFields() {
        StudentListDetailed entity = new StudentListDetailed();
        entity.setStudentId(42);
        entity.setStudentName("Test Student");
        entity.setStudentPassportNumber("P123");
        entity.setStudentBirthdate(LocalDate.of(2000, 1, 15));
        entity.setStudentStudyStatusName("Active");

        StudentListDetailedDto dto = StudentListDetailedMapper.toDto(entity);

        assertNotNull(dto);
        assertEquals(42, dto.getStudentId());
        assertEquals("Test Student", dto.getStudentName());
        assertEquals("P123", dto.getStudentPassportNumber());
        assertEquals(LocalDate.of(2000, 1, 15), dto.getStudentBirthdate());
        assertEquals("Active", dto.getStudentStudyStatusName());
    }
}
