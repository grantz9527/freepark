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
class ParkingLotControllerTest {

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
    void adminCanCreateLot() throws Exception {
        String token = adminToken();
        String code = "lot_" + System.nanoTime();

        mockMvc.perform(post("/api/v1/lots")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Main Lot\",\"code\":\"" + code
                                + "\",\"lotType\":\"PUBLIC\",\"address\":\"Building A\",\"totalSpaces\":120}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Main Lot"))
                .andExpect(jsonPath("$.data.code").value(code))
                .andExpect(jsonPath("$.data.lotType").value("PUBLIC"))
                .andExpect(jsonPath("$.data.totalSpaces").value(120))
                .andExpect(jsonPath("$.data.enabled").value(true));
    }

    @Test
    void authenticatedUserCanListLots() throws Exception {
        String token = adminToken();
        String code = "lot_" + System.nanoTime();

        mockMvc.perform(post("/api/v1/lots")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"List Lot\",\"code\":\"" + code
                                + "\",\"lotType\":\"INTERNAL\",\"totalSpaces\":50}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/lots").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value(code));
    }

    @Test
    void operatorCannotCreateLot() throws Exception {
        String adminToken = adminToken();
        String opUsername = "op_" + System.nanoTime();

        mockMvc.perform(post("/api/v1/operators")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + opUsername
                                + "\",\"password\":\"secret12\",\"displayName\":\"Lot Operator\"}"))
                .andExpect(status().isOk());

        MvcResult operatorLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + opUsername + "\",\"password\":\"secret12\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String operatorToken = jsonMapper.readTree(operatorLogin.getResponse().getContentAsString())
                .get("data").get("token").asString();

        mockMvc.perform(post("/api/v1/lots")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Blocked\",\"code\":\"blocked_lot\",\"lotType\":\"INTERNAL\",\"totalSpaces\":10}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    void duplicateLotCodeRejected() throws Exception {
        String token = adminToken();
        String code = "dup_" + System.nanoTime();

        mockMvc.perform(post("/api/v1/lots")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"First\",\"code\":\"" + code
                                + "\",\"lotType\":\"INTERNAL\",\"totalSpaces\":10}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/lots")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Second\",\"code\":\"" + code
                                + "\",\"lotType\":\"INTERNAL\",\"totalSpaces\":20}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("lot_code_exists"));
    }

    @Test
    void adminCanUpdateLot() throws Exception {
        String token = adminToken();
        String code = "lot_" + System.nanoTime();

        MvcResult create = mockMvc.perform(post("/api/v1/lots")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Old Name\",\"code\":\"" + code
                                + "\",\"lotType\":\"INTERNAL\",\"totalSpaces\":10}"))
                .andExpect(status().isOk())
                .andReturn();
        String lotId = jsonMapper.readTree(create.getResponse().getContentAsString())
                .get("data").get("id").asString();

        mockMvc.perform(put("/api/v1/lots/" + lotId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated Lot\",\"lotType\":\"PUBLIC\",\"address\":\"New Address\",\"totalSpaces\":80,\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated Lot"))
                .andExpect(jsonPath("$.data.code").value(code))
                .andExpect(jsonPath("$.data.lotType").value("PUBLIC"))
                .andExpect(jsonPath("$.data.address").value("New Address"))
                .andExpect(jsonPath("$.data.totalSpaces").value(80))
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

    @Test
    void operatorCannotUpdateLot() throws Exception {
        String adminToken = adminToken();
        String code = "lot_" + System.nanoTime();

        MvcResult create = mockMvc.perform(post("/api/v1/lots")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Protected\",\"code\":\"" + code
                                + "\",\"lotType\":\"INTERNAL\",\"totalSpaces\":5}"))
                .andExpect(status().isOk())
                .andReturn();
        String lotId = jsonMapper.readTree(create.getResponse().getContentAsString())
                .get("data").get("id").asString();

        String opUsername = "op_" + System.nanoTime();
        mockMvc.perform(post("/api/v1/operators")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + opUsername
                                + "\",\"password\":\"secret12\",\"displayName\":\"Lot Operator\"}"))
                .andExpect(status().isOk());

        MvcResult operatorLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + opUsername + "\",\"password\":\"secret12\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String operatorToken = jsonMapper.readTree(operatorLogin.getResponse().getContentAsString())
                .get("data").get("token").asString();

        mockMvc.perform(put("/api/v1/lots/" + lotId)
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hacked\",\"totalSpaces\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    void adminCanUpdateLotInterceptRules() throws Exception {
        String token = adminToken();
        String code = "lot_" + System.nanoTime();

        MvcResult create = mockMvc.perform(post("/api/v1/lots")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Intercept Lot\",\"code\":\"" + code
                                + "\",\"lotType\":\"INTERNAL\",\"totalSpaces\":10}"))
                .andExpect(status().isOk())
                .andReturn();
        String lotId = jsonMapper.readTree(create.getResponse().getContentAsString())
                .get("data").get("id").asString();

        mockMvc.perform(get("/api/v1/lots/" + lotId + "/intercept").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entryRules").isEmpty())
                .andExpect(jsonPath("$.data.exitRules").isEmpty());

        mockMvc.perform(put("/api/v1/lots/" + lotId + "/intercept")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryRules\":[\"ARREARS\",\"BLACKLIST\"],\"exitRules\":[\"BLACKLIST\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entryRules.length()").value(2))
                .andExpect(jsonPath("$.data.exitRules.length()").value(1))
                .andExpect(jsonPath("$.data.exitRules[0]").value("BLACKLIST"));
    }
}
