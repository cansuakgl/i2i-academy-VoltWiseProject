package com.wattsmart.backend.auth.service;

import com.wattsmart.backend.auth.domain.AppUser;
import com.wattsmart.backend.auth.domain.UserSession;
import com.wattsmart.backend.common.service.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthenticatedUserService {

    private final AuthenticatedUserContext authenticatedUserContext;

    public AppUser requireCurrentUser() {
        AuthContext authContext = authenticatedUserContext.get();
        if (authContext == null || authContext.user() == null) {
            throw new UnauthorizedException("Authentication is required.");
        }
        return authContext.user();
    }

    public UserSession requireCurrentSession() {
        AuthContext authContext = authenticatedUserContext.get();
        if (authContext == null || authContext.session() == null) {
            throw new UnauthorizedException("Authentication is required.");
        }
        return authContext.session();
    }
}
