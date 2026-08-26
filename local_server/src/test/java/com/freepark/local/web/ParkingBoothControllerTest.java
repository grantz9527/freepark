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
class ParkingBoothControllerTest {

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

    private String createLane(String token, String lotId, String name) throws Exception {
        String code = "lane_" + System.nanoTime();
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
    void adminCanCreateAndListBooths() throws Exception {
        String token = adminToken();
        String lotId = createLot(token, "Booth Lot");

        mockMvc.perform(post("/api/v1/lots/" + lotId + "/booths")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"东门岗亭\",\"code\":\"B1\",\"location\":\"东门入口旁\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("东门岗亭"))
                .andExpect(jsonPath("$.data.code").value("B1"))
                .andExpect(jsonPath("$.data.location").value("东门入口旁"))
                .andExpect(jsonPath("$.data.lotId").value(lotId))
                .andExpect(jsonPath("$.data.enabled").value(true));

        mockMvc.perform(post("/api/v1/lots/" + lotId + "/booths")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"西门岗亭\",\"code\":\"B2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("西门岗亭"));

        mockMvc.perform(get("/api/v1/lots/" + lotId + "/booths")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].name").exists())
                .andExpect(jsonPath("$.data.items[0].code").exists())
                .andExpect(jsonPath("$.data.items[0].lotId").exists())
                .andExpect(jsonPath("$.data.items[0].enabled").exists());
    }

    @Test
    void duplicateBoothNameRejected() throws Exception {
        String token = adminToken();
        String lotId = createLot(token, "Booth Lot");

        mockMvc.perform(post("/api/v1/lots/" + lotId + "/booths")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"中心岗亭\",\"code\":\"C1\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/lots/" + lotId + "/booths")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"中心岗亭\",\"code\":\"C2\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("booth_name_exists"));
    }

    @Test
    void duplicateBoothCodeRejected() throws Exception {
        String token = adminToken();
        String lotId = createLot(token, "Booth Lot");

        mockMvc.perform(post("/api/v1/lots/" + lotId + "/booths")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"一号岗亭\",\"code\":\"D1\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/lots/" + lotId + "/booths")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"二号岗亭\",\"code\":\"D1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("booth_code_exists"));
    }

    @Test
    void adminCanUpdateBooth() throws Exception {
        String token = adminToken();
        String lotId = createLot(token, "Booth Lot");

        MvcResult create = mockMvc.perform(post("/api/v1/lots/" + lotId + "/booths")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"旧岗亭\",\"code\":\"U1\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String boothId = jsonMapper.readTree(create.getResponse().getContentAsString())
                .get("data").get("id").asString();

        mockMvc.perform(put("/api/v1/lots/" + lotId + "/booths/" + boothId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新岗亭\",\"code\":\"U2\",\"location\":\"南门\",\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("新岗亭"))
                .andExpect(jsonPath("$.data.code").value("U2"))
                .andExpect(jsonPath("$.data.location").value("南门"))
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

    @Test
    void adminCanDeleteBooth() throws Exception {
        String token = adminToken();
        String lotId = createLot(token, "Booth Lot");

        MvcResult create = mockMvc.perform(post("/api/v1/lots/" + lotId + "/booths")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"待删除岗亭\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String boothId = jsonMapper.readTree(create.getResponse().getContentAsString())
                .get("data").get("id").asString();

        mockMvc.perform(delete("/api/v1/lots/" + lotId + "/booths/" + boothId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/lots/" + lotId + "/booths")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void adminCanConfigureBoothLanes() throws Exception {
        String token = adminToken();
        String lotId = createLot(token, "Booth Lot");
        String laneId = createLane(token, lotId, "入口通道");

        MvcResult create = mockMvc.perform(post("/api/v1/lots/" + lotId + "/booths")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"东门岗亭\",\"code\":\"B1\",\"laneIds\":[\"" + laneId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lanes.length()").value(1))
                .andExpect(jsonPath("$.data.lanes[0].id").value(laneId))
                .andExpect(jsonPath("$.data.lanes[0].name").value("入口通道"))
                .andReturn();
        String boothId = jsonMapper.readTree(create.getResponse().getContentAsString())
                .get("data").get("id").asString();

        mockMvc.perform(put("/api/v1/lots/" + lotId + "/booths/" + boothId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"东门岗亭\",\"code\":\"B1\",\"laneIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lanes.length()").value(0));
    }

    @Test
    void boothLaneFromOtherLotRejected() throws Exception {
        String token = adminToken();
        String lotIdA = createLot(token, "Booth Lot A");
        String lotIdB = createLot(token, "Booth Lot B");
        String laneInB = createLane(token, lotIdB, "B场通道");

        mockMvc.perform(post("/api/v1/lots/" + lotIdA + "/booths")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"A场岗亭\",\"laneIds\":[\"" + laneInB + "\"]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));
    }

    @Test
    void operatorCannotCreateBooth() throws Exception {
        String adminToken = adminToken();
        String lotId = createLot(adminToken, "Booth Lot");
        String opUsername = "op_" + System.nanoTime();

        mockMvc.perform(post("/api/v1/operators")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + opUsername
                                + "\",\"password\":\"secret12\",\"displayName\":\"Booth Operator\"}"))
                .andExpect(status().isOk());

        MvcResult operatorLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + opUsername + "\",\"password\":\"secret12\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String operatorToken = jsonMapper.readTree(operatorLogin.getResponse().getContentAsString())
                .get("data").get("token").asString();

        mockMvc.perform(post("/api/v1/lots/" + lotId + "/booths")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Blocked\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
    }
}
