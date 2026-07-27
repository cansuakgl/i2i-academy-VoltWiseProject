package com.wattsmart.backend.auth.repository;

import com.wattsmart.backend.auth.domain.UserNotificationPreferences;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserNotificationPreferencesRepository extends JpaRepository<UserNotificationPreferences, UUID> {
}
