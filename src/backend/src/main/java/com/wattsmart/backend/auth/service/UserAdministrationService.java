package com.wattsmart.backend.auth.service;

import com.wattsmart.backend.auth.api.dto.UpdateUserRolesRequest;
import com.wattsmart.backend.auth.api.dto.UserSummaryResponse;
import com.wattsmart.backend.auth.domain.AppUser;
import com.wattsmart.backend.auth.domain.UserRole;
import com.wattsmart.backend.auth.domain.UserRoleAssignment;
import com.wattsmart.backend.auth.repository.AppUserRepository;
import com.wattsmart.backend.auth.repository.UserRoleAssignmentRepository;
import com.wattsmart.backend.common.service.BadRequestException;
import com.wattsmart.backend.common.service.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserAdministrationService {

    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final AuthorizationService authorizationService;

    @Transactional(readOnly = true)
    public List<UserSummaryResponse> listUsers(AppUser currentUser) {
        authorizationService.requireAdmin(currentUser);
        return appUserRepository.findAllByOrderByFirstNameAscLastNameAsc().stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserSummaryResponse getUser(AppUser currentUser, UUID userId) {
        authorizationService.requireAdmin(currentUser);
        return toSummary(findUser(userId));
    }

    @Transactional
    public UserSummaryResponse updateRoles(AppUser currentUser, UUID userId, UpdateUserRolesRequest request) {
        authorizationService.requireAdmin(currentUser);

        AppUser user = findUser(userId);
        if (!request.roles().contains(UserRole.ADMIN)
                && userRoleAssignmentRepository.findRolesByUserId(userId).contains(UserRole.ADMIN)
                && userRoleAssignmentRepository.countByRole(UserRole.ADMIN) <= 1) {
            throw new BadRequestException("At least one admin user must remain.");
        }

        userRoleAssignmentRepository.deleteByUserId(userId);

        request.roles().forEach(role -> {
            UserRoleAssignment assignment = new UserRoleAssignment();
            assignment.setUser(user);
            assignment.setRole(role);
            userRoleAssignmentRepository.save(assignment);
        });

        return toSummary(user);
    }

    private AppUser findUser(UUID userId) {
        return appUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private UserSummaryResponse toSummary(AppUser user) {
        return new UserSummaryResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getStatus(),
                userRoleAssignmentRepository.findRolesByUserId(user.getId())
        );
    }
}
