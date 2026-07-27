package com.wattsmart.backend.homes.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.wattsmart.backend.auth.domain.AppUser;
import com.wattsmart.backend.auth.domain.UserRole;
import com.wattsmart.backend.auth.domain.UserRoleAssignment;
import com.wattsmart.backend.auth.repository.AppUserRepository;
import com.wattsmart.backend.auth.repository.UserRoleAssignmentRepository;
import com.wattsmart.backend.homes.domain.ApplianceType;
import com.wattsmart.backend.homes.domain.TariffPlan;
import com.wattsmart.backend.homes.repository.ApplianceTypeRepository;
import com.wattsmart.backend.homes.repository.TariffPlanRepository;
import com.wattsmart.backend.integration.PostgresIntegrationTestBase;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class HomeMembershipIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private UserRoleAssignmentRepository userRoleAssignmentRepository;

    @Autowired
    private TariffPlanRepository tariffPlanRepository;

    @Autowired
    private ApplianceTypeRepository applianceTypeRepository;

    @Test
    void adminCanAddListAndRemoveHomeMembersWhileResidentCannotManageMembers() throws Exception {
        UserSession admin = registerLoginAndPromoteToAdmin(uniqueEmail("admin"));
        UserSession owner = registerAndLogin(uniqueEmail("owner"));
        UserSession resident = registerAndLogin(uniqueEmail("resident"));
        UUID homeId = registerHome(owner.token());

        mockMvc.perform(post("/api/homes/{homeId}/members", homeId)
                        .header("Authorization", "Bearer " + resident.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "%s"
                                }
                                """.formatted(resident.userId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Admin access is required."));

        mockMvc.perform(post("/api/homes/{homeId}/members", homeId)
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "%s"
                                }
                                """.formatted(resident.userId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.homeId").value(homeId.toString()))
                .andExpect(jsonPath("$.userId").value(resident.userId().toString()));

        mockMvc.perform(get("/api/homes/{homeId}/members", homeId)
                        .header("Authorization", "Bearer " + resident.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.userId == '%s')]".formatted(resident.userId())).exists());

        mockMvc.perform(delete("/api/homes/{homeId}/members/{userId}", homeId, resident.userId())
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isNoContent());
    }

    private UUID registerHome(String token) throws Exception {
        TariffPlan tariffPlan = createTariffPlan();
        String applianceTypeCode = "MEMBER-FRIDGE-" + UUID.randomUUID();
        createApplianceType(applianceTypeCode);

        MvcResult homeResult = mockMvc.perform(post("/api/homes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "externalKey": "member-home-%s",
                                  "name": "Membership Home",
                                  "countryCode": "TR",
                                  "timezoneName": "Europe/Istanbul",
                                  "billing": {
                                    "tariffPlanId": "%s",
                                    "monthlyUsageLimitKwh": 250.0
                                  },
                                  "appliances": [
                                    {
                                      "applianceCode": "membership-fridge",
                                      "name": "Membership Fridge",
                                      "typeCode": "%s"
                                    }
                                  ]
                                }
                                """.formatted(UUID.randomUUID(), tariffPlan.getId(), applianceTypeCode)))
                .andExpect(status().isCreated())
                .andReturn();

        return UUID.fromString(json(homeResult).get("homeId").asText());
    }

    private UserSession registerLoginAndPromoteToAdmin(String email) throws Exception {
        UserSession session = registerAndLogin(email);
        AppUser user = appUserRepository.findById(session.userId()).orElseThrow();
        if (!userRoleAssignmentRepository.findRolesByUserId(user.getId()).contains(UserRole.ADMIN)) {
            UserRoleAssignment roleAssignment = new UserRoleAssignment();
            roleAssignment.setUser(user);
            roleAssignment.setRole(UserRole.ADMIN);
            userRoleAssignmentRepository.save(roleAssignment);
        }
        return login(email);
    }

    private UserSession registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "StrongPass123!",
                                  "firstName": "Member",
                                  "lastName": "Tester"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated());

        return login(email);
    }

    private UserSession login(String email) throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "StrongPass123!"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginJson = json(loginResult);
        return new UserSession(
                UUID.fromString(loginJson.get("userId").asText()),
                loginJson.get("accessToken").asText());
    }

    private TariffPlan createTariffPlan() {
        TariffPlan tariffPlan = new TariffPlan();
        tariffPlan.setCode("MEMBER-TEST-" + UUID.randomUUID());
        tariffPlan.setName("Membership Test Tariff");
        tariffPlan.setCurrencyCode("TRY");
        tariffPlan.setBaseRatePerKwh(new BigDecimal("1.25"));
        tariffPlan.setEffectiveFrom(LocalDate.now());
        tariffPlan.setActive(true);
        return tariffPlanRepository.save(tariffPlan);
    }

    private void createApplianceType(String code) {
        ApplianceType applianceType = new ApplianceType();
        applianceType.setCode(code);
        applianceType.setDisplayName(code + " Type");
        applianceType.setTypicalWatts(new BigDecimal("150.00"));
        applianceType.setDefaultSafeWattLimit(new BigDecimal("300.00"));
        applianceType.setPeakWattLimit(new BigDecimal("500.00"));
        applianceTypeRepository.save(applianceType);
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private record UserSession(UUID userId, String token) {
    }
}
