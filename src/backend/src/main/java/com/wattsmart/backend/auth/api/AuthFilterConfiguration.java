package com.wattsmart.backend.auth.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wattsmart.backend.auth.service.AuthSessionService;
import com.wattsmart.backend.auth.service.AuthenticatedUserContext;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class AuthFilterConfiguration {

    @Bean
    public AuthTokenFilter authTokenFilter(
            AuthSessionService authSessionService,
            AuthenticatedUserContext authenticatedUserContext,
            ObjectMapper objectMapper
    ) {
        return new AuthTokenFilter(authSessionService, authenticatedUserContext, objectMapper);
    }

    @Bean
    public FilterRegistrationBean<AuthTokenFilter> authTokenFilterRegistration(AuthTokenFilter authTokenFilter) {
        FilterRegistrationBean<AuthTokenFilter> registration = new FilterRegistrationBean<>(authTokenFilter);
        registration.setName("authTokenFilter");
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
