package com.wattsmart.backend.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.wattsmart.backend.integration.PostgresIntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class AuthApiIntegrationTest extends PostgresIntegrationTestBase {

    @Test
    void registersFirstUserAsAdminAndSupportsLoginAndMe() throws Exception {
        String email = uniqueEmail("admin");
        register(email, "StrongPass123!")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email));

        MvcResult loginResult = login(email, "StrongPass123!")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
                .andReturn();

        JsonNode loginJson = json(loginResult);
        String accessToken = loginJson.get("accessToken").asText();
        assertThat(accessToken).isNotBlank();

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"));
    }

    @Test
    void rejectsDuplicateRegistrationAndInvalidLogin() throws Exception {
        String email = uniqueEmail("duplicate");
        register(email, "StrongPass123!").andExpect(status().isCreated());

        register(email, "AnotherPass123!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A user with email '" + email + "' already exists."));

        login(email, "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    void protectedEndpointRequiresBearerToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Missing bearer token."));
    }

    private org.springframework.test.web.servlet.ResultActions register(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "%s",
                          "password": "%s",
                          "firstName": "Test",
                          "lastName": "User"
                        }
                        """.formatted(email, password)));
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "%s",
                          "password": "%s"
                        }
                        """.formatted(email, password)));
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }
}
