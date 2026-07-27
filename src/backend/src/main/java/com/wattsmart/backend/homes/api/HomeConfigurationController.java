package com.wattsmart.backend.homes.api;

import com.wattsmart.backend.homes.api.dto.RegistrationOptionsResponse;
import com.wattsmart.backend.homes.service.HomeConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class HomeConfigurationController {

    private final HomeConfigurationService homeConfigurationService;

    @GetMapping("/registration-options")
    public RegistrationOptionsResponse getRegistrationOptions() {
        return homeConfigurationService.getRegistrationOptions();
    }
}
