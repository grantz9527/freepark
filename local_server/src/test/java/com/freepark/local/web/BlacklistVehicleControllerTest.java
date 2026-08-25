package com.freepark.local.web;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
class BlacklistVehicleControllerTest {

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
                        .content("{\"name\":\"Blacklist Lot\",\"code\":\"" + code
                                + "\",\"lotType\":\"PUBLIC\",\"totalSpaces\":80}"))
                .andExpect(status().isOk())
                .andReturn();
        return jsonMapper.readTree(create.getResponse().getContentAsString())
                .get("data").get("id").asString();
    }

    @Test
    void adminCanManageBlacklistVehicles() throws Exception {
        String token = adminToken();
        String lotId = createLot(token);

        mockMvc.perform(post("/api/v1/lots/" + lotId + "/blacklist-vehicles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plateNumber\":\"京A12345\",\"plateColor\":\"BLUE\",\"ownerName\":\"张三\",\"phone\":\"13800000000\",\"department\":\"访客\",\"startTime\":\"2026-08-23T00:00:00Z\",\"endTime\":\"2026-12-31T23:59:59Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plateNumber").value("京A12345"))
                .andExpect(jsonPath("$.data.plateColor").value("BLUE"))
                .andExpect(jsonPath("$.data.startTime").value("2026-08-23T00:00:00Z"))
                .andExpect(jsonPath("$.data.endTime").value("2026-12-31T23:59:59Z"));

        mockMvc.perform(get("/api/v1/lots/" + lotId + "/blacklist-vehicles")
                        .header("Authorization", "Bearer " + token)
                        .param("plate", "12345"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        MvcResult create = mockMvc.perform(post("/api/v1/lots/" + lotId + "/blacklist-vehicles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plateNumber\":\"京B88888\",\"plateColor\":\"GREEN\",\"ownerName\":\"李四\",\"startTime\":\"2026-01-01T00:00:00Z\",\"endTime\":\"2026-06-30T23:59:59Z\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String vehicleId = jsonMapper.readTree(create.getResponse().getContentAsString())
                .get("data").get("id").asString();

        mockMvc.perform(put("/api/v1/lots/" + lotId + "/blacklist-vehicles/" + vehicleId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plateNumber\":\"京B99999\",\"plateColor\":\"YELLOW\",\"ownerName\":\"李四\",\"enabled\":false,\"startTime\":\"2026-02-01T00:00:00Z\",\"endTime\":\"2026-07-31T23:59:59Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plateNumber").value("京B99999"))
                .andExpect(jsonPath("$.data.plateColor").value("YELLOW"))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.startTime").value("2026-02-01T00:00:00Z"))
                .andExpect(jsonPath("$.data.endTime").value("2026-07-31T23:59:59Z"));

        mockMvc.perform(post("/api/v1/lots/" + lotId + "/blacklist-vehicles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plateNumber\":\"京D00000\",\"plateColor\":\"BLUE\",\"ownerName\":\"钱七\",\"startTime\":\"2026-12-31T00:00:00Z\",\"endTime\":\"2026-01-01T00:00:00Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("blacklist_vehicle_invalid_time_range"));

        mockMvc.perform(post("/api/v1/lots/" + lotId + "/blacklist-vehicles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plateNumber\":\"京B99999\",\"plateColor\":\"BLUE\",\"ownerName\":\"王五\",\"startTime\":\"2026-03-01T00:00:00Z\",\"endTime\":\"2026-08-31T23:59:59Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("blacklist_vehicle_plate_exists"));

        mockMvc.perform(delete("/api/v1/lots/" + lotId + "/blacklist-vehicles/" + vehicleId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void operatorCannotCreateBlacklistVehicle() throws Exception {
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

        mockMvc.perform(post("/api/v1/lots/" + lotId + "/blacklist-vehicles")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plateNumber\":\"京C11111\",\"plateColor\":\"BLUE\",\"ownerName\":\"赵六\",\"startTime\":\"2026-01-01T00:00:00Z\",\"endTime\":\"2026-12-31T23:59:59Z\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    void adminCanImportBlacklistAndDownloadTemplate() throws Exception {
        String token = adminToken();
        String lotId = createLot(token);

        mockMvc.perform(get("/api/v1/lots/" + lotId + "/blacklist-vehicles/import-template")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        byte[] excel = buildAccessListExcel();
        mockMvc.perform(multipart("/api/v1/lots/" + lotId + "/blacklist-vehicles/import")
                        .file(new MockMultipartFile(
                                "file",
                                "blacklist.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                excel))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imported").value(1))
                .andExpect(jsonPath("$.data.skipped").value(1));

        mockMvc.perform(get("/api/v1/lots/" + lotId + "/blacklist-vehicles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    private byte[] buildAccessListExcel() throws Exception {
        String[][] rows = {
            { "车牌号", "车主姓名", "车牌颜色", "电话", "部门", "备注", "开始时间", "结束时间" },
            { "京A20001", "王五", "GREEN", "", "", "", "2026-01-01 00:00", "2026-12-31 23:59" },
            { "京A20001", "赵六", "GREEN", "", "", "", "2026-01-01 00:00", "2026-12-31 23:59" },
        };
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("blacklist");
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r);
                for (int c = 0; c < rows[r].length; c++) {
                    row.createCell(c).setCellValue(rows[r][c]);
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
