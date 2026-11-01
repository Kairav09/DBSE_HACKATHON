package com.smartparking.service;

import com.smartparking.dto.AuthDtos;
import com.smartparking.exception.ApiException;
import com.smartparking.repository.UserRepository;
import com.smartparking.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link AuthService#register}: a duplicate email must be
 * rejected with the 409 conflict from docs/API.md §1, before anything is saved.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;
    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private AuthService authService;

    @Test
    void register_duplicateEmail_rejected409() {
        when(userRepository.existsByEmail("dup@example.com")).thenReturn(true);
        AuthDtos.RegisterRequest req = new AuthDtos.RegisterRequest(
                "Dup User", "dup@example.com", "secret123", "9999999999");

        ApiException ex = assertThrows(ApiException.class, () -> authService.register(req));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("Email already registered"));
        verify(userRepository, never()).save(any());
    }
}
