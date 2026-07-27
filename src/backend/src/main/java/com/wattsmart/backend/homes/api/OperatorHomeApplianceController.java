package com.wattsmart.backend.homes.api;

import com.wattsmart.backend.auth.service.AuthenticatedUserService;
import com.wattsmart.backend.homes.api.dto.ApplianceManagementRequest;
import com.wattsmart.backend.homes.api.dto.ApplianceResponse;
import com.wattsmart.backend.homes.service.ApplianceManagementService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/operator/homes/{homeId}/appliances")
@RequiredArgsConstructor
public class OperatorHomeApplianceController {

    private final AuthenticatedUserService authenticatedUserService;
    private final ApplianceManagementService applianceManagementService;

    @GetMapping
    public List<ApplianceResponse> listAppliances(@PathVariable UUID homeId) {
        return applianceManagementService.listAppliances(authenticatedUserService.requireCurrentUser(), homeId);
    }

    @PostMapping
    public ApplianceResponse addAppliance(
            @PathVariable UUID homeId,
            @Valid @RequestBody ApplianceManagementRequest request
    ) {
        return applianceManagementService.addAppliance(
                authenticatedUserService.requireCurrentUser(),
                homeId,
                request
        );
    }

    @PutMapping("/{applianceId}")
    public ApplianceResponse updateAppliance(
            @PathVariable UUID homeId,
            @PathVariable UUID applianceId,
            @Valid @RequestBody ApplianceManagementRequest request
    ) {
        return applianceManagementService.updateAppliance(
                authenticatedUserService.requireCurrentUser(),
                homeId,
                applianceId,
                request
        );
    }

    @PostMapping("/{applianceId}/deactivate")
    public ApplianceResponse deactivateAppliance(
            @PathVariable UUID homeId,
            @PathVariable UUID applianceId
    ) {
        return applianceManagementService.deactivateAppliance(
                authenticatedUserService.requireCurrentUser(),
                homeId,
                applianceId
        );
    }
}
