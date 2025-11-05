package com.ayu.raksha.card.Ayu.Raksha.Card.controller;

import com.ayu.raksha.card.Ayu.Raksha.Card.models.AppointmentStatus;
import com.ayu.raksha.card.Ayu.Raksha.Card.models.MedicalRecord;
import com.ayu.raksha.card.Ayu.Raksha.Card.models.Role;
import com.ayu.raksha.card.Ayu.Raksha.Card.models.User;
import com.ayu.raksha.card.Ayu.Raksha.Card.repository.AlertRepository;
import com.ayu.raksha.card.Ayu.Raksha.Card.repository.AppointmentRepository;
import com.ayu.raksha.card.Ayu.Raksha.Card.repository.MedicalRecordRepository;
import com.ayu.raksha.card.Ayu.Raksha.Card.repository.UserRepository;
import com.ayu.raksha.card.Ayu.Raksha.Card.service.S3Service;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PatientControllerDashboardTest {

    @Mock private UserRepository userRepository;
    @Mock private S3Service s3Service;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private AlertRepository alertRepository;
    @Mock private MedicalRecordRepository medicalRecordRepository;

    @InjectMocks private PatientController controller;

    @BeforeEach
    void setUp() {
        // Authenticate as DOCTOR so self-check passes for any patientId
        var auth = new UsernamePasswordAuthenticationToken(
                "doctor@example.com",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_DOCTOR"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void dashboard_returns_counts_and_latest_record() {
        String patientCode = "001234567890";
        // Mock patient user
        User patient = new User();
        patient.setId(1L);
        patient.setEmail("patient@example.com");
        patient.setName("Krishna Kumar");
        patient.setRole(Role.ROLE_PATIENT);
        patient.setPatientId(patientCode);
        when(userRepository.findByPatientId(patientCode)).thenReturn(Optional.of(patient));

        // Counts
        when(appointmentRepository.countByPatientAndStatusAndDateGreaterThanEqual(eq(patient), eq(AppointmentStatus.SCHEDULED), any(LocalDate.class)))
                .thenReturn(2L);
        when(alertRepository.countByPatientAndDateGreaterThanEqual(eq(patient), any(LocalDate.class)))
                .thenReturn(3L);

        // Medical records
        MedicalRecord mr1 = new MedicalRecord();
        mr1.setType("Blood Report");
        mr1.setDate(LocalDate.of(2025, 10, 5));
        mr1.setFindings("All normal");
        mr1.setStatus("Normal");

        MedicalRecord mr2 = new MedicalRecord();
        mr2.setType("Prescription");
        mr2.setDate(LocalDate.of(2025, 10, 1));
        mr2.setFindings("");
        mr2.setStatus("Reviewed");

        when(medicalRecordRepository.findTop5ByPatientOrderByDateDescIdDesc(patient)).thenReturn(List.of(mr1, mr2));
        when(medicalRecordRepository.findTopByPatientOrderByDateDescIdDesc(patient)).thenReturn(Optional.of(mr1));

        // Act
        ResponseEntity<?> resp = controller.getPatientDashboard(patientCode);

        // Assert
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        Map<?,?> body = (Map<?, ?>) resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("success")).isEqualTo(true);
        Map<?,?> data = (Map<?, ?>) body.get("data");
        assertThat(data).isNotNull();
        Map<?,?> counts = (Map<?, ?>) data.get("counts");
        assertThat(counts.get("appointmentsUpcoming")).isEqualTo(2L);
        assertThat(counts.get("alertsActive")).isEqualTo(3L);
        assertThat(counts.get("reportsRecent")).isEqualTo(2);
        Map<?,?> latest = (Map<?, ?>) data.get("latestRecord");
        assertThat(latest.get("type")).isEqualTo("Blood Report");
        assertThat(latest.get("date")).isEqualTo("2025-10-05");
        assertThat(latest.get("status")).isEqualTo("Normal");
    }
}

