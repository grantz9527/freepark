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
class SystemSettingsControllerTest {

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
    void authenticatedUserCanReadSystemSettings() throws Exception {
        mockMvc.perform(get("/api/v1/system-settings").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultLocale").exists())
                .andExpect(jsonPath("$.data.timezone").exists())
                .andExpect(jsonPath("$.data.supportedLocales").isArray())
                .andExpect(jsonPath("$.data.supportedTimezones").isArray());
    }

    @Test
    void adminCanUpdateSystemSettings() throws Exception {
        String token = adminToken();

        mockMvc.perform(put("/api/v1/system-settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"defaultLocale\":\"en\",\"timezone\":\"UTC\",\"defaultPlateColor\":\"BLUE\",\"allowedPlateColors\":[\"BLUE\",\"YELLOW\",\"GREEN\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultLocale").value("en"))
                .andExpect(jsonPath("$.data.timezone").value("UTC"))
                .andExpect(jsonPath("$.data.defaultPlateColor").value("BLUE"));

        mockMvc.perform(get("/api/v1/system-settings").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultLocale").value("en"))
                .andExpect(jsonPath("$.data.timezone").value("UTC"));
    }

    @Test
    void operatorCannotUpdateSystemSettings() throws Exception {
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

        mockMvc.perform(put("/api/v1/system-settings")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"defaultLocale\":\"en\",\"timezone\":\"UTC\",\"defaultPlateColor\":\"BLUE\",\"allowedPlateColors\":[\"BLUE\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
    }
}
