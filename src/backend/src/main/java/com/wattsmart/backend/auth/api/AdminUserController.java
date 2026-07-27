package com.wattsmart.backend.auth.api;

import com.wattsmart.backend.auth.api.dto.UpdateUserRolesRequest;
import com.wattsmart.backend.auth.api.dto.UserSummaryResponse;
import com.wattsmart.backend.auth.service.AuthenticatedUserService;
import com.wattsmart.backend.auth.service.UserAdministrationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AuthenticatedUserService authenticatedUserService;
    private final UserAdministrationService userAdministrationService;

    @GetMapping
    public List<UserSummaryResponse> listUsers() {
        return userAdministrationService.listUsers(authenticatedUserService.requireCurrentUser());
    }

    @GetMapping("/{userId}")
    public UserSummaryResponse getUser(@PathVariable UUID userId) {
        return userAdministrationService.getUser(authenticatedUserService.requireCurrentUser(), userId);
    }

    @PutMapping("/{userId}/roles")
    public UserSummaryResponse updateRoles(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserRolesRequest request
    ) {
        return userAdministrationService.updateRoles(authenticatedUserService.requireCurrentUser(), userId, request);
    }
}
