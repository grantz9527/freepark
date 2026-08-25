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
class ParkingBarrierControllerTest {

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

    private String createLot(String token) throws Exception {
        String code = "lot_" + System.nanoTime();
        MvcResult create = mockMvc.perform(post("/api/v1/lots")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Barrier Lot\",\"code\":\"" + code
                                + "\",\"lotType\":\"PUBLIC\",\"totalSpaces\":80}"))
                .andExpect(status().isOk())
                .andReturn();
        return jsonMapper.readTree(create.getResponse().getContentAsString())
                .get("data").get("id").asString();
    }

    private String createLane(String token, String lotId, String name, String code) throws Exception {
        MvcResult create = mockMvc.perform(post("/api/v1/lanes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"code\":\"" + code
                                + "\",\"laneType\":\"ENTRANCE\",\"lotId\":\"" + lotId + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return jsonMapper.readTree(create.getResponse().getContentAsString())
                .get("data").get("id").asString();
    }

    @Test
    void adminCanCreateAndListBarriers() throws Exception {
        String token = adminToken();
        String lotId = createLot(token);
        String laneId = createLane(token, lotId, "东门入口", "E1");

        mockMvc.perform(post("/api/v1/lanes/" + laneId + "/barriers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"东门道闸\",\"code\":\"G1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("东门道闸"))
                .andExpect(jsonPath("$.data.code").value("G1"))
                .andExpect(jsonPath("$.data.laneId").value(laneId))
                .andExpect(jsonPath("$.data.laneName").value("东门入口"))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/lanes/" + laneId + "/barriers")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("东门道闸"))
                .andExpect(jsonPath("$.data[0].code").value("G1"))
                .andExpect(jsonPath("$.data[0].laneName").exists())
                .andExpect(jsonPath("$.data[0].enabled").exists())
                .andExpect(jsonPath("$.data[0].updatedAt").exists());
    }

    @Test
    void duplicateBarrierCodeRejectedOnSameLane() throws Exception {
        String token = adminToken();
        String lotId = createLot(token);
        String laneId = createLane(token, lotId, "入口 A", "A1");

        mockMvc.perform(post("/api/v1/lanes/" + laneId + "/barriers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"道闸 A\",\"code\":\"G1\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/lanes/" + laneId + "/barriers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"道闸 B\",\"code\":\"G1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("barrier_code_exists"));
    }

    @Test
    void adminCanUpdateBarrier() throws Exception {
        String token = adminToken();
        String lotId = createLot(token);
        String laneId = createLane(token, lotId, "入口", "E1");

        MvcResult create = mockMvc.perform(post("/api/v1/lanes/" + laneId + "/barriers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"旧名称\",\"code\":\"U1\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String barrierId = jsonMapper.readTree(create.getResponse().getContentAsString())
                .get("data").get("id").asString();

        mockMvc.perform(put("/api/v1/lanes/" + laneId + "/barriers/" + barrierId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新名称\",\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("新名称"))
                .andExpect(jsonPath("$.data.code").value("U1"))
                .andExpect(jsonPath("$.data.laneId").value(laneId))
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

    @Test
    void operatorCannotCreateBarrier() throws Exception {
        String adminToken = adminToken();
        String lotId = createLot(adminToken);
        String laneId = createLane(adminToken, lotId, "入口", "E1");
        String opUsername = "op_" + System.nanoTime();

        mockMvc.perform(post("/api/v1/operators")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + opUsername
                                + "\",\"password\":\"secret12\",\"displayName\":\"Barrier Operator\"}"))
                .andExpect(status().isOk());

        MvcResult operatorLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + opUsername + "\",\"password\":\"secret12\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String operatorToken = jsonMapper.readTree(operatorLogin.getResponse().getContentAsString())
                .get("data").get("token").asString();

        mockMvc.perform(post("/api/v1/lanes/" + laneId + "/barriers")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Blocked\",\"code\":\"Z1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
    }
}
