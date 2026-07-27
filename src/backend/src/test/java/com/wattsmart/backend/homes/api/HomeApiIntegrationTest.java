package com.wattsmart.backend.homes.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.wattsmart.backend.homes.domain.ApplianceType;
import com.wattsmart.backend.homes.domain.TariffPlan;
import com.wattsmart.backend.homes.repository.ApplianceRepository;
import com.wattsmart.backend.homes.repository.ApplianceTypeRepository;
import com.wattsmart.backend.homes.repository.HomeBillingAccountRepository;
import com.wattsmart.backend.homes.repository.HomeRepository;
import com.wattsmart.backend.homes.repository.TariffPlanRepository;
import com.wattsmart.backend.integration.PostgresIntegrationTestBase;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class HomeApiIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired
    private TariffPlanRepository tariffPlanRepository;

    @Autowired
    private ApplianceTypeRepository applianceTypeRepository;

    @Autowired
    private HomeRepository homeRepository;

    @Autowired
    private ApplianceRepository applianceRepository;

    @Autowired
    private HomeBillingAccountRepository homeBillingAccountRepository;

    @Test
    void registersHomeWithTariffApplianceAndLiveStatus() throws Exception {
        TariffPlan tariffPlan = createTariffPlan();
        createApplianceType("FRIDGE");
        String token = registerAndLogin(uniqueEmail("resident"));
        String externalKey = "home-" + UUID.randomUUID();

        MvcResult homeResult = mockMvc.perform(post("/api/homes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "externalKey": "%s",
                                  "name": "Integration Home",
                                  "city": "Istanbul",
                                  "countryCode": "TR",
                                  "timezoneName": "Europe/Istanbul",
                                  "billing": {
                                    "tariffPlanId": "%s",
                                    "monthlyUsageLimitKwh": 250.0,
                                    "billingCycleStartDay": 1
                                  },
                                  "appliances": [
                                    {
                                      "applianceCode": "fridge-main",
                                      "name": "Main Fridge",
                                      "typeCode": "FRIDGE",
                                      "manufacturer": "WattSmart",
                                      "modelName": "ColdBox 1",
                                      "nominalWattage": 140.0,
                                      "safeWattLimit": 250.0
                                    }
                                  ]
                                }
                                """.formatted(externalKey, tariffPlan.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.externalKey").value(externalKey))
                .andExpect(jsonPath("$.applianceCount").value(1))
                .andReturn();

        JsonNode homeJson = json(homeResult);
        UUID homeId = UUID.fromString(homeJson.get("homeId").asText());

        assertThat(homeRepository.findById(homeId)).isPresent();
        assertThat(applianceRepository.findStatusAppliancesByHomeIds(List.of(homeId))).hasSize(1);
        assertThat(homeBillingAccountRepository.findByHomeId(homeId)).isPresent();

        mockMvc.perform(get("/api/homes/status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homes[0].homeId").value(homeId.toString()))
                .andExpect(jsonPath("$.homes[0].externalKey").value(externalKey))
                .andExpect(jsonPath("$.homes[0].appliances[0].applianceCode").value("fridge-main"));
    }

    @Test
    void rejectsHomeRegistrationWithUnknownApplianceType() throws Exception {
        TariffPlan tariffPlan = createTariffPlan();
        String token = registerAndLogin(uniqueEmail("resident"));

        mockMvc.perform(post("/api/homes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "externalKey": "home-%s",
                                  "name": "Integration Home",
                                  "countryCode": "TR",
                                  "timezoneName": "Europe/Istanbul",
                                  "billing": {
                                    "tariffPlanId": "%s",
                                    "monthlyUsageLimitKwh": 250.0
                                  },
                                  "appliances": [
                                    {
                                      "applianceCode": "unknown-device",
                                      "name": "Unknown Device",
                                      "typeCode": "UNKNOWN"
                                    }
                                  ]
                                }
                                """.formatted(UUID.randomUUID(), tariffPlan.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Unknown appliance type codes: UNKNOWN"));
    }

    @Test
    void registersHomeWithoutExternalKeyOrAppliances() throws Exception {
        TariffPlan tariffPlan = createTariffPlan();
        String token = registerAndLogin(uniqueEmail("resident"));

        MvcResult homeResult = mockMvc.perform(post("/api/homes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Appliance Later Home",
                                  "city": "Istanbul",
                                  "countryCode": "TR",
                                  "timezoneName": "Europe/Istanbul",
                                  "billing": {
                                    "tariffPlanId": "%s",
                                    "monthlyUsageLimitKwh": 250.0
                                  },
                                  "appliances": []
                                }
                                """.formatted(tariffPlan.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.applianceCount").value(0))
                .andReturn();

        JsonNode homeJson = json(homeResult);
        UUID homeId = UUID.fromString(homeJson.get("homeId").asText());

        assertThat(homeJson.get("externalKey").asText()).startsWith("HOME-");
        assertThat(homeRepository.findById(homeId)).isPresent();
        assertThat(applianceRepository.findStatusAppliancesByHomeIds(List.of(homeId))).isEmpty();
        assertThat(homeBillingAccountRepository.findByHomeId(homeId)).isPresent();
    }

    private TariffPlan createTariffPlan() {
        TariffPlan tariffPlan = new TariffPlan();
        tariffPlan.setCode("TEST-" + UUID.randomUUID());
        tariffPlan.setName("Integration Tariff");
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

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "StrongPass123!",
                                  "firstName": "Home",
                                  "lastName": "Tester"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated());

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

        return json(loginResult).get("accessToken").asText();
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }
}
