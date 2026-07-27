package com.wattsmart.backend.homes.api;

import com.wattsmart.backend.auth.service.AuthenticatedUserService;
import com.wattsmart.backend.homes.api.dto.TariffPlanManagementRequest;
import com.wattsmart.backend.homes.api.dto.TariffPlanMilestoneRequest;
import com.wattsmart.backend.homes.api.dto.TariffPlanResponse;
import com.wattsmart.backend.homes.service.TariffPlanManagementService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/tariff-plans")
@RequiredArgsConstructor
public class AdminTariffPlanController {

    private final AuthenticatedUserService authenticatedUserService;
    private final TariffPlanManagementService tariffPlanManagementService;

    @GetMapping
    public List<TariffPlanResponse> listTariffPlans() {
        return tariffPlanManagementService.listTariffPlans(authenticatedUserService.requireCurrentUser());
    }

    @GetMapping("/{tariffPlanId}")
    public TariffPlanResponse getTariffPlan(@PathVariable UUID tariffPlanId) {
        return tariffPlanManagementService.getTariffPlan(
                authenticatedUserService.requireCurrentUser(),
                tariffPlanId
        );
    }

    @PostMapping
    public TariffPlanResponse createTariffPlan(
            @Valid @RequestBody TariffPlanManagementRequest request
    ) {
        return tariffPlanManagementService.createTariffPlan(
                authenticatedUserService.requireCurrentUser(),
                request
        );
    }

    @PutMapping("/{tariffPlanId}")
    public TariffPlanResponse updateTariffPlan(
            @PathVariable UUID tariffPlanId,
            @Valid @RequestBody TariffPlanManagementRequest request
    ) {
        return tariffPlanManagementService.updateTariffPlan(
                authenticatedUserService.requireCurrentUser(),
                tariffPlanId,
                request
        );
    }

    @PutMapping("/{tariffPlanId}/milestones")
    public TariffPlanResponse replaceMilestones(
            @PathVariable UUID tariffPlanId,
            @Valid @RequestBody List<TariffPlanMilestoneRequest> milestones
    ) {
        return tariffPlanManagementService.replaceMilestones(
                authenticatedUserService.requireCurrentUser(),
                tariffPlanId,
                milestones
        );
    }

    @PostMapping("/{tariffPlanId}/deactivate")
    public TariffPlanResponse deactivateTariffPlan(@PathVariable UUID tariffPlanId) {
        return tariffPlanManagementService.deactivateTariffPlan(
                authenticatedUserService.requireCurrentUser(),
                tariffPlanId
        );
    }

    @DeleteMapping("/{tariffPlanId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTariffPlan(@PathVariable UUID tariffPlanId) {
        tariffPlanManagementService.deleteTariffPlan(
                authenticatedUserService.requireCurrentUser(),
                tariffPlanId
        );
    }
}
