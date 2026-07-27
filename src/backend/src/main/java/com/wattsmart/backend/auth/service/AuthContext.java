package com.wattsmart.backend.auth.service;

import com.wattsmart.backend.auth.domain.AppUser;
import com.wattsmart.backend.auth.domain.UserSession;

public record AuthContext(
        AppUser user,
        UserSession session
) {
}
