package com.wattsmart.backend.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

public abstract class IntegrationTestSupport extends PostgresIntegrationTestBase {

    protected static final String TEST_PASSWORD = "StrongPass123!";

    @Autowired
    protected AppUserRepository appUserRepository;

    @Autowired
    protected UserRoleAssignmentRepository userRoleAssignmentRepository;

    @Autowired
    protected TariffPlanRepository tariffPlanRepository;

    @Autowired
    protected ApplianceTypeRepository applianceTypeRepository;

    protected UserSession registerAndLogin(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s",
                                  "firstName": "Integration",
                                  "lastName": "Tester"
                                }
                                """.formatted(email, TEST_PASSWORD)))
                .andExpect(status().isCreated());
        return login(email);
    }

    protected UserSession registerLoginAndPromoteToAdmin(String prefix) throws Exception {
        UserSession session = registerAndLogin(prefix);
        AppUser user = appUserRepository.findById(session.userId()).orElseThrow();
        if (!userRoleAssignmentRepository.findRolesByUserId(user.getId()).contains(UserRole.ADMIN)) {
            UserRoleAssignment assignment = new UserRoleAssignment();
            assignment.setUser(user);
            assignment.setRole(UserRole.ADMIN);
            userRoleAssignmentRepository.save(assignment);
        }
        return login(session.email());
    }

    protected UserSession login(String email) throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, TEST_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginJson = json(loginResult);
        return new UserSession(
                UUID.fromString(loginJson.get("userId").asText()),
                loginJson.get("email").asText(),
                loginJson.get("accessToken").asText());
    }

    protected TariffPlan createTariffPlan(String prefix, BigDecimal baseRatePerKwh) {
        TariffPlan tariffPlan = new TariffPlan();
        tariffPlan.setCode(prefix + "-" + UUID.randomUUID());
        tariffPlan.setName(prefix + " Tariff");
        tariffPlan.setCurrencyCode("TRY");
        tariffPlan.setBaseRatePerKwh(baseRatePerKwh);
        tariffPlan.setEffectiveFrom(LocalDate.now().minusMonths(2));
        tariffPlan.setActive(true);
        return tariffPlanRepository.save(tariffPlan);
    }

    protected ApplianceType createApplianceType(String prefix) {
        ApplianceType applianceType = new ApplianceType();
        applianceType.setCode(prefix + "-" + UUID.randomUUID());
        applianceType.setDisplayName(prefix + " Type");
        applianceType.setTypicalWatts(new BigDecimal("150.00"));
        applianceType.setDefaultSafeWattLimit(new BigDecimal("300.00"));
        applianceType.setPeakWattLimit(new BigDecimal("500.00"));
        return applianceTypeRepository.save(applianceType);
    }

    protected RegisteredHome registerHome(
            String token,
            TariffPlan tariffPlan,
            ApplianceType applianceType,
            BigDecimal monthlyUsageLimitKwh
    ) throws Exception {
        String externalKey = "home-" + UUID.randomUUID();
        String applianceCode = "appliance-" + UUID.randomUUID();
        MvcResult homeResult = mockMvc.perform(post("/api/homes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "externalKey": "%s",
                                  "name": "Integration Home",
                                  "countryCode": "TR",
                                  "timezoneName": "Europe/Istanbul",
                                  "billing": {
                                    "tariffPlanId": "%s",
                                    "monthlyUsageLimitKwh": %s,
                                    "billingCycleStartDay": 1
                                  },
                                  "appliances": [
                                    {
                                      "applianceCode": "%s",
                                      "name": "Integration Appliance",
                                      "typeCode": "%s",
                                      "manufacturer": "WattSmart",
                                      "modelName": "Test Model",
                                      "nominalWattage": 140.0,
                                      "safeWattLimit": 250.0
                                    }
                                  ]
                                }
                                """.formatted(
                                externalKey,
                                tariffPlan.getId(),
                                monthlyUsageLimitKwh.toPlainString(),
                                applianceCode,
                                applianceType.getCode())))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode homeJson = json(homeResult);
        return new RegisteredHome(
                UUID.fromString(homeJson.get("homeId").asText()),
                externalKey,
                applianceCode);
    }

    protected String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    protected record UserSession(UUID userId, String email, String token) {
    }

    protected record RegisteredHome(UUID homeId, String externalKey, String applianceCode) {
    }
}
