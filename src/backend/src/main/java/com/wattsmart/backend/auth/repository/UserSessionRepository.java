package com.wattsmart.backend.auth.repository;

import com.wattsmart.backend.auth.domain.UserSession;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    Optional<UserSession> findByRefreshTokenHashAndRevokedAtIsNullAndExpiresAtAfter(
            String refreshTokenHash,
            OffsetDateTime now
    );
}
