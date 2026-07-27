package com.wattsmart.backend.auth.service;

import com.wattsmart.backend.auth.api.dto.AuthSessionResponse;
import com.wattsmart.backend.auth.domain.AppUser;
import com.wattsmart.backend.auth.domain.UserSession;
import com.wattsmart.backend.auth.domain.UserStatus;
import com.wattsmart.backend.auth.repository.UserSessionRepository;
import com.wattsmart.backend.auth.repository.UserRoleAssignmentRepository;
import com.wattsmart.backend.common.service.UnauthorizedException;
import com.wattsmart.backend.homes.repository.HomeRepository;
import com.wattsmart.backend.homes.repository.HomeUserMembershipRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthSessionService {

    private final UserSessionRepository userSessionRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final HomeUserMembershipRepository homeUserMembershipRepository;
    private final HomeRepository homeRepository;
    private final TokenHashingService tokenHashingService;
    private final AuthProperties authProperties;

    @Transactional
    public AuthSessionResponse createSession(AppUser user, HttpServletRequest request) {
        String rawToken = generateToken();
        OffsetDateTime now = OffsetDateTime.now();

        UserSession session = new UserSession();
        session.setUser(user);
        session.setRefreshTokenHash(tokenHashingService.hash(rawToken));
        session.setUserAgent(request.getHeader("User-Agent"));
        session.setExpiresAt(now.plus(authProperties.getSessionTtl()));
        session.setLastUsedAt(now);
        userSessionRepository.save(session);

        return buildResponse(user, rawToken, session.getExpiresAt());
    }

    @Transactional
    public AuthContext authenticate(String bearerToken) {
        OffsetDateTime now = OffsetDateTime.now();
        UserSession session = userSessionRepository.findByRefreshTokenHashAndRevokedAtIsNullAndExpiresAtAfter(
                        tokenHashingService.hash(bearerToken),
                        now)
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired session."));

        AppUser user = session.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new UnauthorizedException("User account is not active.");
        }

        session.setLastUsedAt(now);
        return new AuthContext(user, session);
    }

    @Transactional
    public void revoke(UserSession session) {
        session.setRevokedAt(OffsetDateTime.now());
    }

    @Transactional(readOnly = true)
    public AuthSessionResponse buildResponse(AppUser user, String accessToken, OffsetDateTime expiresAt) {
        var roles = userRoleAssignmentRepository.findRolesByUserId(user.getId());
        boolean globalHomeAccess = roles.contains(com.wattsmart.backend.auth.domain.UserRole.ADMIN)
                || roles.contains(com.wattsmart.backend.auth.domain.UserRole.OPERATOR);

        return new AuthSessionResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                roles,
                accessToken,
                "Bearer",
                expiresAt,
                globalHomeAccess
                        ? homeRepository.findAllIds()
                        : homeUserMembershipRepository.findAcceptedHomeIdsByUserId(user.getId())
        );
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
