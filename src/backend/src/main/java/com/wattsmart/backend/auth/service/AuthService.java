package com.wattsmart.backend.auth.service;

import com.wattsmart.backend.auth.api.dto.AuthLoginRequest;
import com.wattsmart.backend.auth.api.dto.AuthSessionResponse;
import com.wattsmart.backend.auth.api.dto.UserRegistrationRequest;
import com.wattsmart.backend.auth.api.dto.UserRegistrationResponse;
import com.wattsmart.backend.auth.domain.AppUser;
import com.wattsmart.backend.auth.domain.AuthProvider;
import com.wattsmart.backend.auth.domain.UserNotificationPreferences;
import com.wattsmart.backend.auth.domain.UserRole;
import com.wattsmart.backend.auth.domain.UserRoleAssignment;
import com.wattsmart.backend.auth.domain.UserSession;
import com.wattsmart.backend.auth.domain.UserStatus;
import com.wattsmart.backend.auth.repository.AppUserRepository;
import com.wattsmart.backend.auth.repository.UserNotificationPreferencesRepository;
import com.wattsmart.backend.auth.repository.UserRoleAssignmentRepository;
import com.wattsmart.backend.common.service.BadRequestException;
import com.wattsmart.backend.common.service.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final UserNotificationPreferencesRepository userNotificationPreferencesRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthSessionService authSessionService;

    @Transactional
    public UserRegistrationResponse register(UserRegistrationRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        if (appUserRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new BadRequestException("A user with email '" + normalizedEmail + "' already exists.");
        }

        boolean firstUser = appUserRepository.count() == 0;

        AppUser user = new AppUser();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setStatus(UserStatus.ACTIVE);
        appUserRepository.save(user);

        UserRoleAssignment roleAssignment = new UserRoleAssignment();
        roleAssignment.setUser(user);
        roleAssignment.setRole(firstUser ? UserRole.ADMIN : UserRole.RESIDENT);
        userRoleAssignmentRepository.save(roleAssignment);

        UserNotificationPreferences preferences = new UserNotificationPreferences();
        preferences.setUserId(user.getId());
        userNotificationPreferencesRepository.save(preferences);

        return new UserRegistrationResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                OffsetDateTime.now()
        );
    }

    @Transactional
    public AuthSessionResponse login(AuthLoginRequest request, HttpServletRequest httpRequest) {
        String normalizedEmail = request.email().trim().toLowerCase();
        AppUser user = appUserRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password."));

        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password.");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new UnauthorizedException("User account is not active.");
        }

        user.setLastLoginAt(OffsetDateTime.now());
        return authSessionService.createSession(user, httpRequest);
    }

    @Transactional(readOnly = true)
    public AuthSessionResponse currentSession(AppUser user, UserSession session) {
        return authSessionService.buildResponse(user, null, session.getExpiresAt());
    }

    @Transactional
    public void logout(UserSession session) {
        authSessionService.revoke(session);
    }
}
