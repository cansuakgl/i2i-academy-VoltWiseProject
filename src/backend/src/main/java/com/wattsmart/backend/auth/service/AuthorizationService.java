package com.wattsmart.backend.auth.service;

import com.wattsmart.backend.auth.domain.AppUser;
import com.wattsmart.backend.auth.domain.UserRole;
import com.wattsmart.backend.auth.repository.UserRoleAssignmentRepository;
import com.wattsmart.backend.common.service.ForbiddenException;
import com.wattsmart.backend.homes.repository.HomeUserMembershipRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthorizationService {

    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final HomeUserMembershipRepository homeUserMembershipRepository;

    @Transactional(readOnly = true)
    public List<UserRole> rolesFor(AppUser user) {
        return userRoleAssignmentRepository.findRolesByUserId(user.getId());
    }

    @Transactional(readOnly = true)
    public boolean hasGlobalHomeAccess(AppUser user) {
        List<UserRole> roles = rolesFor(user);
        return roles.contains(UserRole.ADMIN) || roles.contains(UserRole.OPERATOR);
    }

    @Transactional(readOnly = true)
    public boolean isAdmin(AppUser user) {
        return rolesFor(user).contains(UserRole.ADMIN);
    }

    @Transactional(readOnly = true)
    public boolean isAdminOrOperator(AppUser user) {
        return hasGlobalHomeAccess(user);
    }

    @Transactional(readOnly = true)
    public void requireAdmin(AppUser user) {
        if (!isAdmin(user)) {
            throw new ForbiddenException("Admin access is required.");
        }
    }

    @Transactional(readOnly = true)
    public void requireAdminOrOperator(AppUser user) {
        if (!isAdminOrOperator(user)) {
            throw new ForbiddenException("Admin or operator access is required.");
        }
    }

    @Transactional(readOnly = true)
    public boolean canAccessHome(AppUser user, UUID homeId) {
        return hasGlobalHomeAccess(user)
                || homeUserMembershipRepository.existsByHomeIdAndUserIdAndAcceptedAtIsNotNull(homeId, user.getId());
    }
}
