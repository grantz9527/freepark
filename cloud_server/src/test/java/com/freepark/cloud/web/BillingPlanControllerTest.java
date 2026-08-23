package com.freepark.cloud.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BillingPlanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    private String adminToken() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return jsonMapper.readTree(login.getResponse().getContentAsString())
                .get("data").get("token").asString();
    }

    @Test
    void authenticatedUserCanListBillingPlans() throws Exception {
        String token = adminToken();

        mockMvc.perform(get("/api/v1/billing-plans").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].pricingDimension").exists())
                .andExpect(jsonPath("$.data[0].rules.length()").value(2));
    }

    @Test
    void adminCanCreatePlateColorBillingPlan() throws Exception {
        String token = adminToken();
        String code = "plan_" + System.nanoTime();

        mockMvc.perform(post("/api/v1/billing-plans")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Plate Plan\",\"code\":\"" + code
                                + "\",\"pricingDimension\":\"PLATE_COLOR\",\"rules\":[{\"plateColor\":\"BLUE\",\"billingMode\":\"TEMPORARY\",\"freeMinutes\":10,\"hourlyRate\":5},{\"plateColor\":\"WHITE\",\"billingMode\":\"MONTHLY\",\"monthlyRate\":300}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pricingDimension").value("PLATE_COLOR"))
                .andExpect(jsonPath("$.data.rules.length()").value(2));
    }

    @Test
    void adminCanUpdateVehicleLengthBillingPlan() throws Exception {
        String token = adminToken();
        String code = "plan_" + System.nanoTime();

        MvcResult create = mockMvc.perform(post("/api/v1/billing-plans")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Length Plan\",\"code\":\"" + code
                                + "\",\"pricingDimension\":\"VEHICLE_LENGTH\",\"rules\":[{\"minLengthCm\":0,\"maxLengthCm\":500,\"billingMode\":\"TEMPORARY\",\"hourlyRate\":4}]}"))
                .andExpect(status().isOk())
                .andReturn();
        String planId = jsonMapper.readTree(create.getResponse().getContentAsString())
                .get("data").get("id").asString();

        mockMvc.perform(put("/api/v1/billing-plans/" + planId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated Length\",\"pricingDimension\":\"VEHICLE_LENGTH\",\"enabled\":false,\"rules\":[{\"minLengthCm\":0,\"maxLengthCm\":400,\"billingMode\":\"TEMPORARY\",\"hourlyRate\":3},{\"minLengthCm\":401,\"billingMode\":\"TEMPORARY\",\"hourlyRate\":7}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated Length"))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.rules.length()").value(2));
    }
}
