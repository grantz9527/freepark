package com.freepark.local.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class StreamPreviewControllerTest {

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
    void rejectsInvalidStreamUrl() throws Exception {
        String token = adminToken();
        mockMvc.perform(post("/api/v1/barriers/stream-preview")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"streamUrl\":\"file:///tmp/secret.mp4\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("stream_url_invalid"));
    }

    @Test
    void go2rtcUnreachableReturnsPreviewFailed() throws Exception {
        String token = adminToken();
        mockMvc.perform(post("/api/v1/barriers/stream-preview")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"streamUrl\":\"rtsp://192.168.1.64:554/h264/ch1/main/av_stream\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("stream_preview_failed"));
    }

    @Test
    void cameraNameIsAcceptedThenPreviewFailsWithoutGo2rtc() throws Exception {
        String token = adminToken();
        mockMvc.perform(post("/api/v1/barriers/stream-preview")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"streamUrl\":\"cam_acfb0350\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("stream_preview_failed"));
    }

    @Test
    void listsSourcesWhenAuthenticated() throws Exception {
        String token = adminToken();
        mockMvc.perform(get("/api/v1/barriers/stream-preview/sources")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void requiresAuth() throws Exception {
        mockMvc.perform(post("/api/v1/barriers/stream-preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"streamUrl\":\"rtsp://192.168.1.64:554/stream\"}"))
                .andExpect(status().isUnauthorized());
    }
}
