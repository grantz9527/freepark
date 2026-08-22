package com.freepark.local.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ParkingSpaceControllerTest {

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
                        .content("{\"name\":\"Space Lot\",\"code\":\"" + code
                                + "\",\"lotType\":\"INTERNAL\",\"totalSpaces\":100}"))
                .andExpect(status().isOk())
                .andReturn();
        return jsonMapper.readTree(create.getResponse().getContentAsString())
                .get("data").get("id").asString();
    }

    @Test
    void adminCanManageSpaces() throws Exception {
        String token = adminToken();
        String lotId = createLot(token);

        MvcResult location = mockMvc.perform(post("/api/v1/lots/" + lotId + "/locations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"地面\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String locationId = jsonMapper.readTree(location.getResponse().getContentAsString())
                .get("data").get("id").asString();

        MvcResult area = mockMvc.perform(post("/api/v1/lots/" + lotId + "/areas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locationId\":\"" + locationId + "\",\"name\":\"A区\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String areaId = jsonMapper.readTree(area.getResponse().getContentAsString())
                .get("data").get("id").asString();

        mockMvc.perform(post("/api/v1/lots/" + lotId + "/spaces")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"areaId\":\"" + areaId + "\",\"code\":\"1-1001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("1-1001"));

        mockMvc.perform(get("/api/v1/lots/" + lotId + "/spaces")
                        .header("Authorization", "Bearer " + token)
                        .param("areaId", areaId)
                        .param("code", "1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].code").value("1-1001"));

        MvcResult create = mockMvc.perform(post("/api/v1/lots/" + lotId + "/spaces")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"areaId\":\"" + areaId + "\",\"code\":\"1-1002\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String spaceId = jsonMapper.readTree(create.getResponse().getContentAsString())
                .get("data").get("id").asString();

        mockMvc.perform(delete("/api/v1/lots/" + lotId + "/spaces/" + spaceId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "spaces.txt",
                "text/plain",
                "1-2001\n1-2002\n".getBytes());
        mockMvc.perform(multipart("/api/v1/lots/" + lotId + "/spaces/import")
                        .file(file)
                        .param("areaId", areaId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(2));
    }
}
