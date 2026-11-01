package com.smartparking.service;

import com.smartparking.dto.AuthDtos;
import com.smartparking.exception.ApiException;
import com.smartparking.model.Role;
import com.smartparking.model.User;
import com.smartparking.repository.UserRepository;
import com.smartparking.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final CurrentUserService currentUserService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager, JwtService jwtService,
                       CurrentUserService currentUserService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.currentUserService = currentUserService;
    }

    public static AuthDtos.UserResponse toUserResponse(User u) {
        return new AuthDtos.UserResponse(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(),
                u.getRole().name(), u.getCreatedAt());
    }

    @Transactional
    public AuthDtos.UserResponse register(AuthDtos.RegisterRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw ApiException.conflict("Email already registered: " + req.email());
        }
        // Self-registration is ALWAYS USER — ADMIN accounts are seeded only (docs/API.md §1).
        User user = new User(req.fullName(), req.email(), passwordEncoder.encode(req.password()), req.phone(), Role.USER);
        return toUserResponse(userRepository.save(user));
    }

    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest req) {
        // Throws BadCredentialsException -> 401 via GlobalExceptionHandler on wrong credentials.
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(req.email(), req.password()));
        User user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));
        String token = jwtService.generateToken(user.getEmail(), user.getRole().name());
        return new AuthDtos.LoginResponse(token,
                new AuthDtos.UserSummary(user.getId(), user.getFullName(), user.getEmail(), user.getRole().name()));
    }

    public AuthDtos.UserResponse me() {
        return toUserResponse(currentUserService.getCurrentUser());
    }
}
