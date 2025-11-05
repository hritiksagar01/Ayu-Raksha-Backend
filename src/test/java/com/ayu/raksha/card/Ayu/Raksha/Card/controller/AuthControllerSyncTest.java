package com.ayu.raksha.card.Ayu.Raksha.Card.controller;

import com.ayu.raksha.card.Ayu.Raksha.Card.dto.SyncRequest;
import com.ayu.raksha.card.Ayu.Raksha.Card.models.Role;
import com.ayu.raksha.card.Ayu.Raksha.Card.models.User;
import com.ayu.raksha.card.Ayu.Raksha.Card.repository.UserRepository;
import com.ayu.raksha.card.Ayu.Raksha.Card.security.jwt.JwtTokenProvider;
import com.ayu.raksha.card.Ayu.Raksha.Card.service.AuthService;
import com.ayu.raksha.card.Ayu.Raksha.Card.service.SupabaseAdminService;
import com.ayu.raksha.card.Ayu.Raksha.Card.service.SupabaseTokenValidator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class AuthControllerSyncTest {

    @Mock
    private AuthService authService; // not used in sync path

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SupabaseAdminService supabaseAdminService;

    @Mock
    private SupabaseTokenValidator supabaseTokenValidator;

    @InjectMocks
    private AuthController authController;

    @Test
    void syncUser_createsPatientWith12DigitPatientCode_whenNewPatient() {
        // Arrange
        String accessToken = "test-token";
        SyncRequest req = new SyncRequest();
        req.setAccessToken(accessToken);
        req.setUserType("patient");
        req.setEmail("patient@example.com");
        req.setName("Test Patient");
        req.setPhone("+91 9999999999");

        // Mock Supabase token validation response
        Map<String, Object> supabaseUser = new HashMap<>();
        supabaseUser.put("valid", true);
        supabaseUser.put("email", "patient@example.com");
        supabaseUser.put("name", "Test Patient");
        supabaseUser.put("phone", "+91 9999999999");
        supabaseUser.put("role", "patient");
        supabaseUser.put("userId", "supabase-user-123");
        when(supabaseTokenValidator.validateAndGetUser(accessToken)).thenReturn(supabaseUser);

        when(jwtTokenProvider.validateToken(accessToken)).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromJWT(accessToken)).thenReturn("patient@example.com");
        when(jwtTokenProvider.getRolesFromJWT(accessToken)).thenReturn(List.of("PATIENT"));
        when(jwtTokenProvider.getUserIdFromJWT(accessToken)).thenReturn("supabase-user-123");

        when(userRepository.findByEmail("patient@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByPatientId(anyString())).thenReturn(Optional.empty());
        // Save should return the same user instance; capture to inspect
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        ResponseEntity<?> resp = authController.syncUser(new HttpHeaders(), req);

        // Assert
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        Object body = resp.getBody();
        assertThat(body).isInstanceOf(Map.class);
        Map<?,?> map = (Map<?, ?>) body;
        assertThat(map.get("success")).isEqualTo(true);
        Map<?,?> data = (Map<?, ?>) map.get("data");
        assertThat(data).isNotNull();
        Map<?,?> userMap = (Map<?, ?>) data.get("user");
        assertThat(userMap).isNotNull();
        String patientCode = (String) userMap.get("patientCode");
        assertThat(patientCode).isNotNull();
        assertThat(patientCode).hasSize(12).matches("\\d{12}");

        // Also assert that the persisted user got a 12-digit patientId and role set
        User saved = captor.getValue();
        assertThat(saved.getRole()).isEqualTo(Role.ROLE_PATIENT);
        assertThat(saved.getPatientId()).isNotNull();
        assertThat(saved.getPatientId()).hasSize(12).matches("\\d{12}");

        // Verify that Supabase user metadata was updated with patientCode
        verify(supabaseAdminService).updateUserMetadata(eq("supabase-user-123"), argThat(m ->
                m != null && patientCode.equals(m.get("patientCode"))
        ));
    }
}
