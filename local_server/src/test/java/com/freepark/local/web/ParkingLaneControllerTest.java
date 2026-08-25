package com.freepark.local.web;

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
class ParkingLaneControllerTest {

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

    private String createLot(String token, String name) throws Exception {
        String code = "lot_" + System.nanoTime();
        MvcResult create = mockMvc.perform(post("/api/v1/lots")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"code\":\"" + code
                                + "\",\"lotType\":\"PUBLIC\",\"totalSpaces\":80}"))
                .andExpect(status().isOk())
                .andReturn();
        return jsonMapper.readTree(create.getResponse().getContentAsString())
                .get("data").get("id").asString();
    }

    @Test
    void adminCanCreateAndListLanes() throws Exception {
        String token = adminToken();
        String lotId = createLot(token, "Lane Lot");

        mockMvc.perform(post("/api/v1/lanes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"东门入口\",\"code\":\"E1\",\"laneType\":\"ENTRANCE\",\"lotId\":\""
                                + lotId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("东门入口"))
                .andExpect(jsonPath("$.data.code").value("E1"))
                .andExpect(jsonPath("$.data.laneType").value("ENTRANCE"))
                .andExpect(jsonPath("$.data.lotId").value(lotId))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());

        mockMvc.perform(post("/api/v1/lanes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"西门出口\",\"code\":\"X1\",\"laneType\":\"EXIT\",\"lotId\":\""
                                + lotId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.laneType").value("EXIT"));

        mockMvc.perform(post("/api/v1/lanes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"南门双向\",\"code\":\"B1\",\"laneType\":\"BIDIRECTIONAL\",\"lotId\":\""
                                + lotId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.laneType").value("BIDIRECTIONAL"));

        mockMvc.perform(get("/api/v1/lanes").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].name").exists())
                .andExpect(jsonPath("$.data[0].code").exists())
                .andExpect(jsonPath("$.data[0].laneType").exists())
                .andExpect(jsonPath("$.data[0].lotId").exists())
                .andExpect(jsonPath("$.data[0].enabled").exists())
                .andExpect(jsonPath("$.data[0].updatedAt").exists());
    }

    @Test
    void adminCanConnectLaneToTwoLots() throws Exception {
        String token = adminToken();
        String outerLotId = createLot(token, "外场");
        String innerLotId = createLot(token, "内场");

        mockMvc.perform(post("/api/v1/lanes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"内外通道\",\"code\":\"N1\",\"laneType\":\"BIDIRECTIONAL\",\"lotId\":\""
                                + outerLotId + "\",\"linkedLotId\":\"" + innerLotId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lotId").value(outerLotId))
                .andExpect(jsonPath("$.data.linkedLotId").value(innerLotId))
                .andExpect(jsonPath("$.data.lotName").value("外场"))
                .andExpect(jsonPath("$.data.linkedLotName").value("内场"));

        mockMvc.perform(get("/api/v1/lanes")
                        .header("Authorization", "Bearer " + token)
                        .param("lotId", innerLotId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].code").value("N1"));
    }

    @Test
    void duplicateConnectedLotsRejected() throws Exception {
        String token = adminToken();
        String lotId = createLot(token, "Same Lot");

        mockMvc.perform(post("/api/v1/lanes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"重复车场\",\"code\":\"D1\",\"laneType\":\"ENTRANCE\",\"lotId\":\""
                                + lotId + "\",\"linkedLotId\":\"" + lotId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("lane_lots_duplicate"));
    }

    @Test
    void duplicateLaneCodeRejected() throws Exception {
        String token = adminToken();
        String lotId = createLot(token, "Lane Lot");
        String otherLotId = createLot(token, "Other Lot");

        mockMvc.perform(post("/api/v1/lanes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"入口 A\",\"code\":\"A1\",\"laneType\":\"ENTRANCE\",\"lotId\":\""
                                + lotId + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/lanes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"入口 B\",\"code\":\"A1\",\"laneType\":\"EXIT\",\"lotId\":\""
                                + otherLotId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("lane_code_exists"));
    }

    @Test
    void adminCanUpdateLane() throws Exception {
        String token = adminToken();
        String lotId = createLot(token, "Lane Lot");
        String linkedLotId = createLot(token, "Linked Lot");

        MvcResult create = mockMvc.perform(post("/api/v1/lanes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"旧名称\",\"code\":\"U1\",\"laneType\":\"ENTRANCE\",\"lotId\":\""
                                + lotId + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String laneId = jsonMapper.readTree(create.getResponse().getContentAsString())
                .get("data").get("id").asString();

        mockMvc.perform(put("/api/v1/lanes/" + laneId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新名称\",\"laneType\":\"BIDIRECTIONAL\",\"enabled\":false,\"lotId\":\""
                                + lotId + "\",\"linkedLotId\":\"" + linkedLotId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("新名称"))
                .andExpect(jsonPath("$.data.code").value("U1"))
                .andExpect(jsonPath("$.data.laneType").value("BIDIRECTIONAL"))
                .andExpect(jsonPath("$.data.linkedLotId").value(linkedLotId))
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

    @Test
    void operatorCannotCreateLane() throws Exception {
        String adminToken = adminToken();
        String lotId = createLot(adminToken, "Lane Lot");
        String opUsername = "op_" + System.nanoTime();

        mockMvc.perform(post("/api/v1/operators")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + opUsername
                                + "\",\"password\":\"secret12\",\"displayName\":\"Lane Operator\"}"))
                .andExpect(status().isOk());

        MvcResult operatorLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + opUsername + "\",\"password\":\"secret12\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String operatorToken = jsonMapper.readTree(operatorLogin.getResponse().getContentAsString())
                .get("data").get("token").asString();

        mockMvc.perform(post("/api/v1/lanes")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Blocked\",\"code\":\"Z1\",\"laneType\":\"ENTRANCE\",\"lotId\":\""
                                + lotId + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
    }
}
