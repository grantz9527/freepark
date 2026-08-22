package com.freepark.local.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class I18nControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void usesEnglishByDefault() throws Exception {
        mockMvc.perform(get("/api/v1/i18n"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.welcome").value("Welcome to FreePark Local"));
    }

    @Test
    void usesAcceptLanguageHeader() throws Exception {
        mockMvc.perform(get("/api/v1/i18n").header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.locale").value("zh-CN"))
                .andExpect(jsonPath("$.data.welcome").value("欢迎使用 FreePark 本地服务"));
    }

    @Test
    void usesLangQueryParameter() throws Exception {
        mockMvc.perform(get("/api/v1/i18n").param("lang", "ja"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.welcome").value("FreePark ローカルへようこそ"));
    }
}
