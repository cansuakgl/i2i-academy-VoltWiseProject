package com.wattsmart.backend.auth.api;

import com.wattsmart.backend.auth.api.dto.NotificationPreferencesResponse;
import com.wattsmart.backend.auth.api.dto.UpdateNotificationPreferencesRequest;
import com.wattsmart.backend.auth.service.AuthenticatedUserService;
import com.wattsmart.backend.auth.service.NotificationPreferencesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me/notification-preferences")
@RequiredArgsConstructor
public class UserNotificationPreferencesController {

    private final AuthenticatedUserService authenticatedUserService;
    private final NotificationPreferencesService notificationPreferencesService;

    @GetMapping
    public NotificationPreferencesResponse getPreferences() {
        return notificationPreferencesService.getPreferences(authenticatedUserService.requireCurrentUser());
    }

    @PutMapping
    public NotificationPreferencesResponse updatePreferences(
            @Valid @RequestBody UpdateNotificationPreferencesRequest request
    ) {
        return notificationPreferencesService.updatePreferences(
                authenticatedUserService.requireCurrentUser(),
                request
        );
    }
}
