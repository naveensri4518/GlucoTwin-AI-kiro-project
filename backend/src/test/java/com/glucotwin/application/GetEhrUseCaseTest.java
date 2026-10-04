package com.glucotwin.application;

import com.glucotwin.domain.shared.PatientId;
import com.glucotwin.domain.shared.ResourceNotFoundException;
import com.glucotwin.domain.twin.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetEhrUseCaseTest {

    @Mock
    private EhrRepository ehrRepository;

    @InjectMocks
    private GetEhrUseCase useCase;

    @Test
    void execute_returnsEhrWhenFound() {
        PatientId patientId = PatientId.random();
        EhrRecord record = buildRecord(patientId);
        when(ehrRepository.findByPatientId(patientId)).thenReturn(Optional.of(record));

        EhrRecord result = useCase.execute(patientId);

        assertThat(result.patientId()).isEqualTo(patientId);
        assertThat(result.sex()).isEqualTo(Sex.MALE);
        assertThat(result.bmi()).isEqualTo(28.5);
    }

    @Test
    void execute_throws404WhenNotFound() {
        PatientId patientId = PatientId.random();
        when(ehrRepository.findByPatientId(patientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(patientId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(patientId.toString());
    }

    private EhrRecord buildRecord(PatientId patientId) {
        return new EhrRecord(UUID.randomUUID(), patientId,
                LocalDate.of(1975, 6, 15), Sex.MALE, 28.5,
                LocalDate.of(2015, 3, 1), 7.2, 6.1, List.of(), List.of());
    }
}
