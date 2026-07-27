package com.wattsmart.backend.homes.api;

import com.wattsmart.backend.auth.service.AuthenticatedUserService;
import com.wattsmart.backend.homes.api.dto.AssignHomeTariffPlanRequest;
import com.wattsmart.backend.homes.api.dto.HomeTariffPlanResponse;
import com.wattsmart.backend.homes.service.HomeTariffPlanService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/homes/{homeId}/tariff-plans")
@RequiredArgsConstructor
public class AdminHomeTariffPlanController {

    private final AuthenticatedUserService authenticatedUserService;
    private final HomeTariffPlanService homeTariffPlanService;

    @GetMapping
    public List<HomeTariffPlanResponse> getTariffHistory(@PathVariable UUID homeId) {
        return homeTariffPlanService.getTariffHistory(authenticatedUserService.requireCurrentUser(), homeId);
    }

    @PostMapping
    public HomeTariffPlanResponse assignTariffPlan(
            @PathVariable UUID homeId,
            @Valid @RequestBody AssignHomeTariffPlanRequest request
    ) {
        return homeTariffPlanService.assignTariffPlan(
                authenticatedUserService.requireCurrentUser(),
                homeId,
                request
        );
    }
}
