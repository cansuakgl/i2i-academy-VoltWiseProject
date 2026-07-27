package com.wattsmart.backend.auth.service;

import org.springframework.stereotype.Component;

@Component
public class AuthenticatedUserContext {

    private static final ThreadLocal<AuthContext> CURRENT = new ThreadLocal<>();

    public void set(AuthContext authContext) {
        CURRENT.set(authContext);
    }

    public AuthContext get() {
        return CURRENT.get();
    }

    public void clear() {
        CURRENT.remove();
    }
}
