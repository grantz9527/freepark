package com.freepark.local.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class PatternAllowlistControllerTest {

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
                        .content("{\"name\":\"Pattern Lot\",\"code\":\"" + code
                                + "\",\"lotType\":\"PUBLIC\",\"totalSpaces\":80}"))
                .andExpect(status().isOk())
                .andReturn();
        return jsonMapper.readTree(create.getResponse().getContentAsString())
                .get("data").get("id").asString();
    }

    @Test
    void adminCanManagePatternAllowlist() throws Exception {
        String token = adminToken();
        String lotId = createLot(token);

        mockMvc.perform(post("/api/v1/lots/" + lotId + "/pattern-allowlist")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"警车\",\"pattern\":\".*警$\",\"remark\":\"公安车辆放行\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("警车"))
                .andExpect(jsonPath("$.data.pattern").value(".*警$"))
                .andExpect(jsonPath("$.data.enabled").value(true));

        mockMvc.perform(get("/api/v1/lots/" + lotId + "/pattern-allowlist")
                        .header("Authorization", "Bearer " + token)
                        .param("keyword", "警"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        MvcResult create = mockMvc.perform(post("/api/v1/lots/" + lotId + "/pattern-allowlist")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"垃圾车\",\"pattern\":\".*环卫.*\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String entryId = jsonMapper.readTree(create.getResponse().getContentAsString())
                .get("data").get("id").asString();

        mockMvc.perform(put("/api/v1/lots/" + lotId + "/pattern-allowlist/" + entryId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"环卫车\",\"pattern\":\".*环卫.*\",\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("环卫车"))
                .andExpect(jsonPath("$.data.enabled").value(false));

        mockMvc.perform(post("/api/v1/lots/" + lotId + "/pattern-allowlist")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"无效\",\"pattern\":\"[未闭合\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("pattern_allowlist_invalid_pattern"));

        mockMvc.perform(post("/api/v1/lots/" + lotId + "/pattern-allowlist")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"警车\",\"pattern\":\".*POLICE.*\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("pattern_allowlist_name_exists"));

        mockMvc.perform(delete("/api/v1/lots/" + lotId + "/pattern-allowlist/" + entryId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void operatorCannotCreatePatternAllowlist() throws Exception {
        String adminToken = adminToken();
        String lotId = createLot(adminToken);
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

        mockMvc.perform(post("/api/v1/lots/" + lotId + "/pattern-allowlist")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"警车\",\"pattern\":\".*警$\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
    }
}
