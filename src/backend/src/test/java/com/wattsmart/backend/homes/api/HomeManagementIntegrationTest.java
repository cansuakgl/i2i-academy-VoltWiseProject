package com.wattsmart.backend.homes.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.wattsmart.backend.integration.IntegrationTestSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class HomeManagementIntegrationTest extends IntegrationTestSupport {

    @Test
    void adminManagesTariffPlansAndResidentIsRejected() throws Exception {
        UserSession admin = registerLoginAndPromoteToAdmin("tariff-admin");
        UserSession resident = registerAndLogin("tariff-resident");
        String code = "MGMT-" + UUID.randomUUID();

        String createRequest = tariffRequest(code, "Managed Tariff", "2.10", true);
        mockMvc.perform(post("/api/admin/tariff-plans")
                        .header("Authorization", "Bearer " + resident.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andExpect(status().isForbidden());

        MvcResult createResult = mockMvc.perform(post("/api/admin/tariff-plans")
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code.toUpperCase()))
                .andExpect(jsonPath("$.milestones[0].milestone").value("PCT_80"))
                .andExpect(jsonPath("$.milestones[1].stage").value("PENALTY"))
                .andReturn();

        JsonNode created = json(createResult);
        UUID tariffPlanId = UUID.fromString(created.get("tariffPlanId").asText());

        mockMvc.perform(put("/api/admin/tariff-plans/{tariffPlanId}", tariffPlanId)
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tariffRequest(code, "Managed Tariff Updated", "2.25", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Managed Tariff Updated"))
                .andExpect(jsonPath("$.baseRatePerKwh").value(2.25));

        mockMvc.perform(put("/api/admin/tariff-plans/{tariffPlanId}/milestones", tariffPlanId)
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {
                                    "milestone": "PCT_100",
                                    "stage": "WARNING"
                                  },
                                  {
                                    "milestone": "PCT_150",
                                    "stage": "PENALTY",
                                    "penaltyMultiplier": 3.0
                                  }
                                ]
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.milestones[0].milestone").value("PCT_100"))
                .andExpect(jsonPath("$.milestones[1].penaltyMultiplier").value(3.0));

        mockMvc.perform(post("/api/admin/tariff-plans/{tariffPlanId}/deactivate", tariffPlanId)
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(delete("/api/admin/tariff-plans/{tariffPlanId}", tariffPlanId)
                        .header("Authorization", "Bearer " + resident.token()))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/admin/tariff-plans/{tariffPlanId}", tariffPlanId)
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/tariff-plans/{tariffPlanId}", tariffPlanId)
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCanAddUpdateAndDeactivateAppliancesWhileResidentIsRejected() throws Exception {
        UserSession admin = registerLoginAndPromoteToAdmin("appliance-admin");
        UserSession resident = registerAndLogin("appliance-resident");
        var tariffPlan = createTariffPlan("APPLIANCE-MGMT", new BigDecimal("1.40"));
        var baseType = createApplianceType("APPLIANCE-BASE");
        var managedType = createApplianceType("APPLIANCE-MANAGED");
        RegisteredHome home = registerHome(resident.token(), tariffPlan, baseType, new BigDecimal("250.000"));

        String addRequest = applianceRequest("dryer-main", "Main Dryer", managedType.getCode(), "WattSmart", "Dryer 1", "850.0", "1000.0");
        mockMvc.perform(post("/api/operator/homes/{homeId}/appliances", home.homeId())
                        .header("Authorization", "Bearer " + resident.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addRequest))
                .andExpect(status().isForbidden());

        MvcResult addResult = mockMvc.perform(post("/api/operator/homes/{homeId}/appliances", home.homeId())
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applianceCode").value("dryer-main"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();

        UUID applianceId = UUID.fromString(json(addResult).get("applianceId").asText());

        mockMvc.perform(get("/api/operator/homes/{homeId}/appliances", home.homeId())
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.applianceCode == 'dryer-main')]").exists());

        mockMvc.perform(put("/api/operator/homes/{homeId}/appliances/{applianceId}", home.homeId(), applianceId)
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applianceRequest("dryer-main-renamed", "Updated Dryer", managedType.getCode(), "WattSmart", "Dryer 2", "900.0", "1100.0")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applianceCode").value("dryer-main-renamed"))
                .andExpect(jsonPath("$.name").value("Updated Dryer"))
                .andExpect(jsonPath("$.safeWattLimit").value(1100.0));

        mockMvc.perform(post("/api/operator/homes/{homeId}/appliances/{applianceId}/deactivate", home.homeId(), applianceId)
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    private String tariffRequest(String code, String name, String baseRate, boolean active) {
        return """
                {
                  "code": "%s",
                  "name": "%s",
                  "description": "Managed in integration tests",
                  "currencyCode": "TRY",
                  "baseRatePerKwh": %s,
                  "effectiveFrom": "%s",
                  "active": %s,
                  "milestones": [
                    {
                      "milestone": "PCT_80",
                      "stage": "WARNING"
                    },
                    {
                      "milestone": "PCT_120",
                      "stage": "PENALTY",
                      "penaltyMultiplier": 2.0
                    }
                  ]
                }
                """.formatted(code, name, baseRate, LocalDate.now().minusDays(1), active);
    }

    private String applianceRequest(
            String applianceCode,
            String name,
            String typeCode,
            String manufacturer,
            String modelName,
            String nominalWattage,
            String safeWattLimit
    ) {
        return """
                {
                  "applianceCode": "%s",
                  "name": "%s",
                  "typeCode": "%s",
                  "manufacturer": "%s",
                  "modelName": "%s",
                  "nominalWattage": %s,
                  "safeWattLimit": %s,
                  "displayOrder": 3
                }
                """.formatted(applianceCode, name, typeCode, manufacturer, modelName, nominalWattage, safeWattLimit);
    }
}
