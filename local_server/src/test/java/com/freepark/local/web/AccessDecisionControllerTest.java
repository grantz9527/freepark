package com.freepark.local.web;

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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccessDecisionControllerTest {

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

    private String createLot(String token, String lotType) throws Exception {
        String code = "lot_" + System.nanoTime();
        MvcResult create = mockMvc.perform(post("/api/v1/lots")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Decision Lot\",\"code\":\"" + code
                                + "\",\"lotType\":\"" + lotType + "\",\"totalSpaces\":50}"))
                .andExpect(status().isOk())
                .andReturn();
        return jsonMapper.readTree(create.getResponse().getContentAsString())
                .get("data").get("id").asString();
    }

    private String createLane(String token, String lotId) throws Exception {
        String code = "lane_" + System.nanoTime();
        MvcResult create = mockMvc.perform(post("/api/v1/lanes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Entry Lane\",\"code\":\"" + code
                                + "\",\"laneType\":\"ENTRANCE\",\"lotId\":\"" + lotId + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return jsonMapper.readTree(create.getResponse().getContentAsString())
                .get("data").get("id").asString();
    }

    private void addVehicle(String token, String lotId, String path, String plate) throws Exception {
        mockMvc.perform(post("/api/v1/lots/" + lotId + "/" + path)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plateNumber\":\"" + plate + "\",\"plateColor\":\"BLUE\",\"ownerName\":\"测试\","
                                + "\"startTime\":\"2026-01-01T00:00:00Z\",\"endTime\":\"2099-12-31T23:59:59Z\"}"))
                .andExpect(status().isOk());
    }

    private ResultActions decide(String token, String lotId, String laneId, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/lots/" + lotId + "/access-decision")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    @Test
    void blacklistInterceptRespectsLotDirectionRules() throws Exception {
        String token = adminToken();
        String lotId = createLot(token, "PUBLIC");
        String laneId = createLane(token, lotId);
        addVehicle(token, lotId, "blacklist-vehicles", "京A12345");

        // Entry does not intercept blacklist by default.
        decide(token, lotId, laneId,
                "{\"laneId\":\"" + laneId + "\",\"plateNumber\":\"京A12345\",\"plateColor\":\"BLUE\",\"direction\":\"ENTRANCE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("ALLOWED"));

        // Enable blacklist intercept for entry; now intercepted.
        mockMvc.perform(put("/api/v1/lots/" + lotId + "/intercept")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryRules\":[\"BLACKLIST\"],\"exitRules\":[]}"))
                .andExpect(status().isOk());

        decide(token, lotId, laneId,
                "{\"laneId\":\"" + laneId + "\",\"plateNumber\":\"京A12345\",\"plateColor\":\"BLUE\",\"direction\":\"ENTRANCE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("INTERCEPTED"))
                .andExpect(jsonPath("$.data.remark").value("blacklisted_vehicle"));

        // Exit rules are still off, so exit is allowed.
        decide(token, lotId, laneId,
                "{\"laneId\":\"" + laneId + "\",\"plateNumber\":\"京A12345\",\"plateColor\":\"BLUE\",\"direction\":\"EXIT\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("ALLOWED"));
    }

    @Test
    void whitelistFirstOrderAllowsBlacklistedPlate() throws Exception {
        String token = adminToken();
        String lotId = createLot(token, "PUBLIC");
        String laneId = createLane(token, lotId);
        addVehicle(token, lotId, "blacklist-vehicles", "京A12345");
        addVehicle(token, lotId, "whitelist-vehicles", "京A12345");

        mockMvc.perform(put("/api/v1/lots/" + lotId + "/intercept")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryRules\":[\"BLACKLIST\"],\"exitRules\":[\"BLACKLIST\"]}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/lots/" + lotId + "/access-judgment")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ruleOrder\":[\"WHITELIST\",\"PATTERN_ALLOWLIST\",\"BLACKLIST\"]}"))
                .andExpect(status().isOk());

        decide(token, lotId, laneId,
                "{\"laneId\":\"" + laneId + "\",\"plateNumber\":\"京A12345\",\"plateColor\":\"BLUE\",\"direction\":\"ENTRANCE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("ALLOWED"))
                .andExpect(jsonPath("$.data.remark").value("whitelist_match"));
    }

    @Test
    void internalLotEntryRequiresRegisteredVehicle() throws Exception {
        String token = adminToken();
        String lotId = createLot(token, "INTERNAL");
        String laneId = createLane(token, lotId);

        decide(token, lotId, laneId,
                "{\"laneId\":\"" + laneId + "\",\"plateNumber\":\"京A12345\",\"plateColor\":\"BLUE\",\"direction\":\"ENTRANCE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("INTERCEPTED"))
                .andExpect(jsonPath("$.data.remark").value("not_internal_vehicle"));

        mockMvc.perform(post("/api/v1/lots/" + lotId + "/internal-vehicles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plateNumber\":\"京A12345\",\"plateColor\":\"BLUE\",\"ownerName\":\"张三\"}"))
                .andExpect(status().isOk());

        decide(token, lotId, laneId,
                "{\"laneId\":\"" + laneId + "\",\"plateNumber\":\"京A12345\",\"plateColor\":\"BLUE\",\"direction\":\"ENTRANCE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("ALLOWED"));
    }

    @Test
    void plateColorInterceptAndExitWithoutSession() throws Exception {
        String token = adminToken();
        String lotId = createLot(token, "PUBLIC");
        String laneId = createLane(token, lotId);

        decide(token, lotId, laneId,
                "{\"laneId\":\"" + laneId + "\",\"plateNumber\":\"京A12345\",\"plateColor\":\"BLUE\","
                        + "\"direction\":\"ENTRANCE\",\"interceptColors\":[\"BLUE\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("INTERCEPTED"))
                .andExpect(jsonPath("$.data.remark").value("plate_color_intercept"));

        decide(token, lotId, laneId,
                "{\"laneId\":\"" + laneId + "\",\"plateNumber\":\"京A12345\",\"plateColor\":\"BLUE\","
                        + "\"direction\":\"EXIT\",\"hasOpenSession\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("ALLOWED"))
                .andExpect(jsonPath("$.data.remark").value("no_open_session"));
    }
}
