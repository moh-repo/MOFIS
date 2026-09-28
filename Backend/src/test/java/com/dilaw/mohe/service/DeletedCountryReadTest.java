package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentCountry;
import com.dilaw.mohe.repository.StudentCountryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Live QA L4 (#13, 25 Sep 2026): reading a deleted country through the System Coding
 * API answered 200 with an empty body; every other lookup answers 404.
 */
@ExtendWith(MockitoExtension.class)
class DeletedCountryReadTest {

    @Mock private StudentCountryRepository repository;
    @InjectMocks private StudentCountryService service;

    @Test
    @DisplayName("a country that is not there is a 404")
    void missingIsNotFound() {
        when(repository.findById(1099)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getStudentCountryById(1099))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(404));
    }

    @Test
    @DisplayName("a country that is there is returned")
    void presentIsReturned() {
        StudentCountry c = new StudentCountry();
        when(repository.findById(1)).thenReturn(Optional.of(c));
        assertThat(service.getStudentCountryById(1)).isSameAs(c);
    }
}
