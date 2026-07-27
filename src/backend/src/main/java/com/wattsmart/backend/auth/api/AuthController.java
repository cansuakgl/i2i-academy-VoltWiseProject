package com.wattsmart.backend.auth.api;

import com.wattsmart.backend.auth.api.dto.AuthLoginRequest;
import com.wattsmart.backend.auth.api.dto.AuthSessionResponse;
import com.wattsmart.backend.auth.api.dto.UserRegistrationRequest;
import com.wattsmart.backend.auth.api.dto.UserRegistrationResponse;
import com.wattsmart.backend.auth.service.AuthService;
import com.wattsmart.backend.auth.service.AuthenticatedUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthenticatedUserService authenticatedUserService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserRegistrationResponse register(@Valid @RequestBody UserRegistrationRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthSessionResponse login(@Valid @RequestBody AuthLoginRequest request, HttpServletRequest httpRequest) {
        return authService.login(request, httpRequest);
    }

    @GetMapping("/me")
    public AuthSessionResponse me() {
        return authService.currentSession(
                authenticatedUserService.requireCurrentUser(),
                authenticatedUserService.requireCurrentSession()
        );
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        authService.logout(authenticatedUserService.requireCurrentSession());
    }
}
