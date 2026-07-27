package com.wattsmart.backend.auth.service;

import com.wattsmart.backend.auth.api.dto.NotificationPreferencesResponse;
import com.wattsmart.backend.auth.api.dto.UpdateNotificationPreferencesRequest;
import com.wattsmart.backend.auth.domain.AppUser;
import com.wattsmart.backend.auth.domain.UserNotificationPreferences;
import com.wattsmart.backend.auth.repository.UserNotificationPreferencesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationPreferencesService {

    private final UserNotificationPreferencesRepository userNotificationPreferencesRepository;

    @Transactional(readOnly = true)
    public NotificationPreferencesResponse getPreferences(AppUser user) {
        return userNotificationPreferencesRepository.findById(user.getId())
                .map(this::toResponse)
                .orElseGet(() -> toResponse(defaultPreferences(user)));
    }

    @Transactional
    public NotificationPreferencesResponse updatePreferences(
            AppUser user,
            UpdateNotificationPreferencesRequest request
    ) {
        UserNotificationPreferences preferences = userNotificationPreferencesRepository.findById(user.getId())
                .orElseGet(() -> defaultPreferences(user));
        preferences.setEmailEnabled(request.emailEnabled());
        preferences.setUsageMilestoneEnabled(request.usageMilestoneEnabled());
        preferences.setAnomalyAlertEnabled(request.anomalyAlertEnabled());
        preferences.setMonthlySummaryEnabled(request.monthlySummaryEnabled());
        return toResponse(userNotificationPreferencesRepository.save(preferences));
    }

    private UserNotificationPreferences defaultPreferences(AppUser user) {
        UserNotificationPreferences preferences = new UserNotificationPreferences();
        preferences.setUserId(user.getId());
        return preferences;
    }

    private NotificationPreferencesResponse toResponse(UserNotificationPreferences preferences) {
        return new NotificationPreferencesResponse(
                preferences.getUserId(),
                preferences.isEmailEnabled(),
                preferences.isUsageMilestoneEnabled(),
                preferences.isAnomalyAlertEnabled(),
                preferences.isMonthlySummaryEnabled()
        );
    }
}
