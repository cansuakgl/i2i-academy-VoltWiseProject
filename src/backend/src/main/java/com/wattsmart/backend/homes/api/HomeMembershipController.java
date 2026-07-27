package com.wattsmart.backend.homes.api;

import com.wattsmart.backend.auth.service.AuthenticatedUserService;
import com.wattsmart.backend.homes.api.dto.AddHomeMemberRequest;
import com.wattsmart.backend.homes.api.dto.HomeMemberResponse;
import com.wattsmart.backend.homes.service.HomeMembershipService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/homes/{homeId}/members")
@RequiredArgsConstructor
public class HomeMembershipController {

    private final AuthenticatedUserService authenticatedUserService;
    private final HomeMembershipService homeMembershipService;

    @GetMapping
    public List<HomeMemberResponse> listMembers(@PathVariable UUID homeId) {
        return homeMembershipService.listMembers(authenticatedUserService.requireCurrentUser(), homeId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HomeMemberResponse addMember(
            @PathVariable UUID homeId,
            @Valid @RequestBody AddHomeMemberRequest request
    ) {
        return homeMembershipService.addMember(
                authenticatedUserService.requireCurrentUser(),
                homeId,
                request.userId()
        );
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable UUID homeId, @PathVariable UUID userId) {
        homeMembershipService.removeMember(authenticatedUserService.requireCurrentUser(), homeId, userId);
    }
}
