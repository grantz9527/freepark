package com.freepark.local.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.freepark.local.domain.ParkingSession;
import com.freepark.local.domain.ParkingSessionRepository;
import com.freepark.local.domain.ParkingSessionStatus;
import com.freepark.local.domain.PlateColor;
import com.freepark.local.parkingflow.service.LotOccupancyTracker;

import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccessDecisionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private ParkingSessionRepository sessions;

    @Autowired
    private LotOccupancyTracker occupancy;

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
        return createLot(token, lotType, 50);
    }

    private String createLot(String token, String lotType, int totalSpaces) throws Exception {
        String code = "lot_" + System.nanoTime();
        MvcResult create = mockMvc.perform(post("/api/v1/lots")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Decision Lot\",\"code\":\"" + code
                                + "\",\"lotType\":\"" + lotType + "\",\"totalSpaces\":" + totalSpaces + "}"))
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

    private void occupyLot(String lotId, String laneId, String plate) {
        sessions.save(new ParkingSession(
                UUID.fromString(lotId),
                "Decision Lot",
                plate,
                PlateColor.BLUE,
                Instant.now(),
                UUID.fromString(laneId),
                "Entry Lane",
                null,
                null));
        occupancy.onChanged(UUID.fromString(lotId), null, ParkingSessionStatus.OPEN);
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
    void arrearsInterceptHoldsEntryWhenConfiguredAndFeeDue() throws Exception {
        String token = adminToken();
        String lotId = createLot(token, "PUBLIC");
        String laneId = createLane(token, lotId);

        // Not configured: a due amount is reported but the vehicle still passes.
        decide(token, lotId, laneId,
                "{\"laneId\":\"" + laneId + "\",\"plateNumber\":\"京A12345\",\"plateColor\":\"BLUE\","
                        + "\"direction\":\"ENTRANCE\",\"dueAmount\":8.5}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("ALLOWED"));

        // Enable arrears intercept for entry; a positive due amount now intercepts.
        mockMvc.perform(put("/api/v1/lots/" + lotId + "/intercept")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryRules\":[\"ARREARS\"],\"exitRules\":[]}"))
                .andExpect(status().isOk());

        decide(token, lotId, laneId,
                "{\"laneId\":\"" + laneId + "\",\"plateNumber\":\"京A12345\",\"plateColor\":\"BLUE\","
                        + "\"direction\":\"ENTRANCE\",\"dueAmount\":8.5}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("INTERCEPTED"))
                .andExpect(jsonPath("$.data.remark").value("fee_pending"));

        // Zero due amount is not intercepted.
        decide(token, lotId, laneId,
                "{\"laneId\":\"" + laneId + "\",\"plateNumber\":\"京A12345\",\"plateColor\":\"BLUE\","
                        + "\"direction\":\"ENTRANCE\",\"dueAmount\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("ALLOWED"));

        // Exit rules are still off, so the same due amount passes on exit.
        decide(token, lotId, laneId,
                "{\"laneId\":\"" + laneId + "\",\"plateNumber\":\"京A12345\",\"plateColor\":\"BLUE\","
                        + "\"direction\":\"EXIT\",\"dueAmount\":8.5}")
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
    void whitelistDoesNotMatchWhenPlateColorDiffers() throws Exception {
        String token = adminToken();
        String lotId = createLot(token, "PUBLIC");
        String laneId = createLane(token, lotId);
        addVehicle(token, lotId, "whitelist-vehicles", "鲁Q3614学");

        decide(token, lotId, laneId,
                "{\"laneId\":\"" + laneId + "\",\"plateNumber\":\"鲁Q3614学\",\"plateColor\":\"YELLOW\",\"direction\":\"ENTRANCE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("ALLOWED"))
                .andExpect(jsonPath("$.data.remark").value(""));

        decide(token, lotId, laneId,
                "{\"laneId\":\"" + laneId + "\",\"plateNumber\":\"鲁Q3614学\",\"plateColor\":\"BLUE\",\"direction\":\"ENTRANCE\"}")
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
                "{\"laneId\":\"" + laneId + "\",\"plateNumber\":\"京A12345\",\"plateColor\":\"YELLOW\",\"direction\":\"ENTRANCE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("INTERCEPTED"))
                .andExpect(jsonPath("$.data.remark").value("not_internal_vehicle"));

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

    @Test
    void fullOccupancyInterceptsEntryWhenLotIsFull() throws Exception {
        String token = adminToken();
        String lotId = createLot(token, "PUBLIC", 1);
        String laneId = createLane(token, lotId);

        mockMvc.perform(put("/api/v1/lots/" + lotId + "/intercept")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryRules\":[\"FULL\"],\"exitRules\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entryRules[0]").value("FULL"));

        decide(token, lotId, laneId,
                "{\"laneId\":\"" + laneId + "\",\"plateNumber\":\"京A12345\",\"plateColor\":\"BLUE\",\"direction\":\"ENTRANCE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("ALLOWED"));

        occupyLot(lotId, laneId, "京A00001");

        decide(token, lotId, laneId,
                "{\"laneId\":\"" + laneId + "\",\"plateNumber\":\"京A12345\",\"plateColor\":\"BLUE\",\"direction\":\"ENTRANCE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("INTERCEPTED"))
                .andExpect(jsonPath("$.data.remark").value("lot_full"));

        decide(token, lotId, laneId,
                "{\"laneId\":\"" + laneId + "\",\"plateNumber\":\"京A00001\",\"plateColor\":\"BLUE\",\"direction\":\"ENTRANCE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("ALLOWED"));

        decide(token, lotId, laneId,
                "{\"laneId\":\"" + laneId + "\",\"plateNumber\":\"京A99999\",\"plateColor\":\"BLUE\",\"direction\":\"EXIT\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("ALLOWED"));
    }

    @Test
    void fullOccupancyDoesNotInterceptWhenTotalSpacesIsZero() throws Exception {
        String token = adminToken();
        String lotId = createLot(token, "PUBLIC", 0);
        String laneId = createLane(token, lotId);

        mockMvc.perform(put("/api/v1/lots/" + lotId + "/intercept")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryRules\":[\"FULL\"],\"exitRules\":[]}"))
                .andExpect(status().isOk());

        decide(token, lotId, laneId,
                "{\"laneId\":\"" + laneId + "\",\"plateNumber\":\"京A12345\",\"plateColor\":\"BLUE\",\"direction\":\"ENTRANCE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("ALLOWED"));
    }

    @Test
    void fullOccupancyIsNotBypassedByWhitelist() throws Exception {
        String token = adminToken();
        String lotId = createLot(token, "PUBLIC", 1);
        String laneId = createLane(token, lotId);
        addVehicle(token, lotId, "whitelist-vehicles", "京A12345");

        mockMvc.perform(put("/api/v1/lots/" + lotId + "/intercept")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryRules\":[\"FULL\"],\"exitRules\":[]}"))
                .andExpect(status().isOk());

        occupyLot(lotId, laneId, "京A00001");

        decide(token, lotId, laneId,
                "{\"laneId\":\"" + laneId + "\",\"plateNumber\":\"京A12345\",\"plateColor\":\"BLUE\",\"direction\":\"ENTRANCE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("INTERCEPTED"))
                .andExpect(jsonPath("$.data.remark").value("lot_full"));
    }
}
