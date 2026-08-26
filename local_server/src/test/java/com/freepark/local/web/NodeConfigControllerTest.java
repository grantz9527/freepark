package com.freepark.local.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class NodeConfigControllerTest {

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
    void adminCanReadNodeSettings() throws Exception {
        mockMvc.perform(get("/api/v1/node-settings").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("OFFLINE"))
                .andExpect(jsonPath("$.data.mqttPasswordSet").value(false))
                .andExpect(jsonPath("$.data.updatedAt").exists());
    }

    @Test
    void adminCanUpdateNodeSettingsToEdge() throws Exception {
        String token = adminToken();

        mockMvc.perform(put("/api/v1/node-settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"EDGE\",\"mqttHost\":\"192.168.1.50\",\"mqttPort\":1883,"
                                + "\"mqttClientId\":\"edge-01\",\"mqttUsername\":\"parking\","
                                + "\"mqttPassword\":\"secret\",\"mqttTopicPrefix\":\"freepark/edge/\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("EDGE"))
                .andExpect(jsonPath("$.data.mqttHost").value("192.168.1.50"))
                .andExpect(jsonPath("$.data.mqttPort").value(1883))
                .andExpect(jsonPath("$.data.mqttPasswordSet").value(true));

        mockMvc.perform(get("/api/v1/node-settings").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("EDGE"))
                .andExpect(jsonPath("$.data.mqttTopicPrefix").value("freepark/edge"));
    }

    @Test
    void edgeModeWithoutMqttHostIsRejected() throws Exception {
        String token = adminToken();

        mockMvc.perform(put("/api/v1/node-settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"EDGE\",\"mqttHost\":\"\",\"mqttPort\":1883}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_node_config"));
    }

    @Test
    void operatorCannotUpdateNodeSettings() throws Exception {
        String adminToken = adminToken();
        String username = "op_" + System.nanoTime();

        mockMvc.perform(post("/api/v1/operators")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username
                                + "\",\"password\":\"secret12\",\"displayName\":\"Test Operator\"}"))
                .andExpect(status().isOk());

        MvcResult operatorLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"secret12\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String operatorToken = jsonMapper.readTree(operatorLogin.getResponse().getContentAsString())
                .get("data").get("token").asString();

        mockMvc.perform(put("/api/v1/node-settings")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"EDGE\",\"mqttHost\":\"192.168.1.50\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
    }
}
