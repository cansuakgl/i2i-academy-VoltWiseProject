package com.wattsmart.backend.auth.api;

import com.wattsmart.backend.auth.service.AuthContext;
import com.wattsmart.backend.auth.service.AuthSessionService;
import com.wattsmart.backend.auth.service.AuthenticatedUserContext;
import com.wattsmart.backend.common.api.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wattsmart.backend.common.service.UnauthorizedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class AuthTokenFilter extends OncePerRequestFilter {

    private final AuthSessionService authSessionService;
    private final AuthenticatedUserContext authenticatedUserContext;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (!path.startsWith("/api/")) {
            return true;
        }
        return path.equals("/api/auth/register")
                || path.equals("/api/auth/login")
                || path.equals("/api-docs")
                || path.startsWith("/api-docs/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String authorization = request.getHeader("Authorization");
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                writeUnauthorized(response, "Missing bearer token.");
                return;
            }

            String token = authorization.substring("Bearer ".length()).trim();
            AuthContext authContext = authSessionService.authenticate(token);
            authenticatedUserContext.set(authContext);
            filterChain.doFilter(request, response);
        } catch (UnauthorizedException exception) {
            writeError(response, HttpStatus.UNAUTHORIZED, exception.getMessage());
        } catch (RuntimeException exception) {
            writeError(response, HttpStatus.INTERNAL_SERVER_ERROR, "Authentication failed unexpectedly.");
        } finally {
            authenticatedUserContext.clear();
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        writeError(response, HttpStatus.UNAUTHORIZED, message);
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }

        response.setStatus(status.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(
                response.getOutputStream(),
                new ApiErrorResponse(
                        OffsetDateTime.now(),
                        status.value(),
                        status.getReasonPhrase(),
                        message != null && !message.isBlank() ? message : status.getReasonPhrase(),
                        List.of()));
    }
}
