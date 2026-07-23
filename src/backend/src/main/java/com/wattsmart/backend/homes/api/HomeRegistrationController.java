package com.wattsmart.backend.homes.api;

import com.wattsmart.backend.homes.api.dto.HomeRegistrationRequest;
import com.wattsmart.backend.homes.api.dto.HomeRegistrationResponse;
import com.wattsmart.backend.homes.api.dto.HomeStatusResponse;
import com.wattsmart.backend.homes.service.HomeRegistrationService;
import com.wattsmart.backend.homes.service.HomeStatusService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/homes")
@RequiredArgsConstructor
public class HomeRegistrationController {

    private final HomeRegistrationService homeRegistrationService;
    private final HomeStatusService homeStatusService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HomeRegistrationResponse registerHome(@Valid @RequestBody HomeRegistrationRequest request) {
        return homeRegistrationService.register(request);
    }

    @GetMapping("/status")
    public HomeStatusResponse getHomeStatus() {
        return homeStatusService.getDashboardStatus();
    }
}
