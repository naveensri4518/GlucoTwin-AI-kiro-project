package com.glucotwin.application;

import com.glucotwin.domain.patient.Patient;
import com.glucotwin.domain.patient.PatientRepository;
import com.glucotwin.domain.shared.PatientId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListPatientsUseCaseTest {

    @Mock
    private PatientRepository patientRepository;

    @InjectMocks
    private ListPatientsUseCase useCase;

    @Test
    void execute_returnsPagedPatients() {
        Pageable pageable = PageRequest.of(0, 20);
        Patient p1 = new Patient(PatientId.random(), Instant.now());
        Patient p2 = new Patient(PatientId.random(), Instant.now());
        Page<Patient> page = new PageImpl<>(List.of(p1, p2), pageable, 2);
        when(patientRepository.findAll(pageable)).thenReturn(page);

        Page<Patient> result = useCase.execute(pageable);

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).containsExactly(p1, p2);
    }

    @Test
    void execute_returnsEmptyPageWhenNoPatients() {
        Pageable pageable = PageRequest.of(0, 20);
        when(patientRepository.findAll(pageable)).thenReturn(Page.empty(pageable));

        Page<Patient> result = useCase.execute(pageable);

        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void execute_delegatesPageableToRepository() {
        Pageable pageable = PageRequest.of(2, 10);
        when(patientRepository.findAll(pageable)).thenReturn(Page.empty(pageable));

        useCase.execute(pageable);

        verify(patientRepository).findAll(pageable);
    }
}
