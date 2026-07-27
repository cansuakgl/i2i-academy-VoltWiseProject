package com.wattsmart.backend.homes.service;

import com.wattsmart.backend.auth.domain.AppUser;
import com.wattsmart.backend.auth.domain.UserRole;
import com.wattsmart.backend.auth.repository.AppUserRepository;
import com.wattsmart.backend.auth.service.AuthorizationService;
import com.wattsmart.backend.common.service.BadRequestException;
import com.wattsmart.backend.common.service.ResourceNotFoundException;
import com.wattsmart.backend.common.service.UnauthorizedException;
import com.wattsmart.backend.homes.api.dto.HomeMemberResponse;
import com.wattsmart.backend.homes.domain.Home;
import com.wattsmart.backend.homes.domain.HomeUserMembership;
import com.wattsmart.backend.homes.repository.HomeRepository;
import com.wattsmart.backend.homes.repository.HomeUserMembershipRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HomeMembershipService {

    private final HomeRepository homeRepository;
    private final HomeUserMembershipRepository homeUserMembershipRepository;
    private final AppUserRepository appUserRepository;
    private final AuthorizationService authorizationService;

    @Transactional(readOnly = true)
    public List<HomeMemberResponse> listMembers(AppUser currentUser, UUID homeId) {
        requireHomeExists(homeId);
        if (!authorizationService.canAccessHome(currentUser, homeId)) {
            throw new UnauthorizedException("You do not have access to this home.");
        }

        return homeUserMembershipRepository.findMembersByHomeId(homeId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public HomeMemberResponse addMember(AppUser currentUser, UUID homeId, UUID userId) {
        authorizationService.requireAdmin(currentUser);

        Home home = homeRepository.findById(homeId)
                .orElseThrow(() -> new ResourceNotFoundException("Home not found: " + homeId));
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (authorizationService.rolesFor(user).stream().anyMatch(role -> role == UserRole.ADMIN || role == UserRole.OPERATOR)) {
            throw new BadRequestException("Admin and operator users already have global home access.");
        }
        if (homeUserMembershipRepository.existsByHomeIdAndUserId(homeId, userId)) {
            throw new BadRequestException("User is already linked to this home.");
        }

        HomeUserMembership membership = new HomeUserMembership();
        membership.setHome(home);
        membership.setUser(user);
        membership.setAcceptedAt(OffsetDateTime.now());
        return toResponse(homeUserMembershipRepository.save(membership));
    }

    @Transactional
    public void removeMember(AppUser currentUser, UUID homeId, UUID userId) {
        authorizationService.requireAdmin(currentUser);
        requireHomeExists(homeId);

        HomeUserMembership membership = homeUserMembershipRepository.findByHomeIdAndUserId(homeId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Home membership not found."));
        homeUserMembershipRepository.delete(membership);
    }

    private void requireHomeExists(UUID homeId) {
        if (!homeRepository.existsById(homeId)) {
            throw new ResourceNotFoundException("Home not found: " + homeId);
        }
    }

    private HomeMemberResponse toResponse(HomeUserMembership membership) {
        AppUser user = membership.getUser();
        return new HomeMemberResponse(
                membership.getId(),
                membership.getHome().getId(),
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                membership.getCreatedAt(),
                membership.getAcceptedAt()
        );
    }
}
