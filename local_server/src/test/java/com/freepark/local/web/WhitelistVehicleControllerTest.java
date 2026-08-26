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
class WhitelistVehicleControllerTest {

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
                        .content("{\"name\":\"Whitelist Lot\",\"code\":\"" + code
                                + "\",\"lotType\":\"PUBLIC\",\"totalSpaces\":80}"))
                .andExpect(status().isOk())
                .andReturn();
        return jsonMapper.readTree(create.getResponse().getContentAsString())
                .get("data").get("id").asString();
    }

    @Test
    void adminCanManageWhitelistVehicles() throws Exception {
        String token = adminToken();
        String lotId = createLot(token);

        mockMvc.perform(post("/api/v1/lots/" + lotId + "/whitelist-vehicles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plateNumber\":\"京A12345\",\"plateColor\":\"BLUE\",\"ownerName\":\"张三\",\"type\":\"VISITOR\",\"phone\":\"13800000000\",\"department\":\"访客\",\"startTime\":\"2026-08-23T00:00:00Z\",\"endTime\":\"2026-12-31T23:59:59Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plateNumber").value("京A12345"))
                .andExpect(jsonPath("$.data.plateColor").value("BLUE"))
                .andExpect(jsonPath("$.data.type").value("VISITOR"))
                .andExpect(jsonPath("$.data.startTime").value("2026-08-23T00:00:00Z"))
                .andExpect(jsonPath("$.data.endTime").value("2026-12-31T23:59:59Z"));

        mockMvc.perform(get("/api/v1/lots/" + lotId + "/internal-vehicles")
                        .header("Authorization", "Bearer " + token)
                        .param("plate", "12345"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].plateNumber").value("京A12345"))
                .andExpect(jsonPath("$.data.items[0].ownerName").value("张三"))
                .andExpect(jsonPath("$.data.items[0].type").value("VISITOR"));

        mockMvc.perform(get("/api/v1/lots/" + lotId + "/whitelist-vehicles")
                        .header("Authorization", "Bearer " + token)
                        .param("plate", "12345"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        MvcResult create = mockMvc.perform(post("/api/v1/lots/" + lotId + "/whitelist-vehicles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plateNumber\":\"京B88888\",\"plateColor\":\"GREEN\",\"ownerName\":\"李四\",\"type\":\"TENANT\",\"startTime\":\"2026-01-01T00:00:00Z\",\"endTime\":\"2026-06-30T23:59:59Z\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String vehicleId = jsonMapper.readTree(create.getResponse().getContentAsString())
                .get("data").get("id").asString();

        mockMvc.perform(put("/api/v1/lots/" + lotId + "/whitelist-vehicles/" + vehicleId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plateNumber\":\"京B99999\",\"plateColor\":\"YELLOW\",\"ownerName\":\"李四\",\"type\":\"OWNER\",\"enabled\":false,\"startTime\":\"2026-02-01T00:00:00Z\",\"endTime\":\"2026-07-31T23:59:59Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plateNumber").value("京B99999"))
                .andExpect(jsonPath("$.data.plateColor").value("YELLOW"))
                .andExpect(jsonPath("$.data.type").value("OWNER"))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.startTime").value("2026-02-01T00:00:00Z"))
                .andExpect(jsonPath("$.data.endTime").value("2026-07-31T23:59:59Z"));

        mockMvc.perform(get("/api/v1/lots/" + lotId + "/internal-vehicles")
                        .header("Authorization", "Bearer " + token)
                        .param("plate", "99999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].plateNumber").value("京B99999"))
                .andExpect(jsonPath("$.data.items[0].plateColor").value("YELLOW"))
                .andExpect(jsonPath("$.data.items[0].type").value("OWNER"))
                .andExpect(jsonPath("$.data.items[0].enabled").value(false));

        mockMvc.perform(post("/api/v1/lots/" + lotId + "/whitelist-vehicles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plateNumber\":\"京D00000\",\"plateColor\":\"BLUE\",\"ownerName\":\"钱七\",\"startTime\":\"2026-12-31T00:00:00Z\",\"endTime\":\"2026-01-01T00:00:00Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("whitelist_vehicle_invalid_time_range"));

        mockMvc.perform(post("/api/v1/lots/" + lotId + "/whitelist-vehicles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plateNumber\":\"京B99999\",\"plateColor\":\"BLUE\",\"ownerName\":\"王五\",\"startTime\":\"2026-03-01T00:00:00Z\",\"endTime\":\"2026-08-31T23:59:59Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("whitelist_vehicle_plate_exists"));

        mockMvc.perform(delete("/api/v1/lots/" + lotId + "/whitelist-vehicles/" + vehicleId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void operatorCannotCreateWhitelistVehicle() throws Exception {
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

        mockMvc.perform(post("/api/v1/lots/" + lotId + "/whitelist-vehicles")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plateNumber\":\"京C11111\",\"plateColor\":\"BLUE\",\"ownerName\":\"赵六\",\"startTime\":\"2026-01-01T00:00:00Z\",\"endTime\":\"2026-12-31T23:59:59Z\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    void adminCanImportWhitelistAndDownloadTemplate() throws Exception {
        String token = adminToken();
        String lotId = createLot(token);

        mockMvc.perform(get("/api/v1/lots/" + lotId + "/whitelist-vehicles/import-template")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        byte[] excel = buildAccessListExcel();
        mockMvc.perform(multipart("/api/v1/lots/" + lotId + "/whitelist-vehicles/import")
                        .file(new MockMultipartFile(
                                "file",
                                "whitelist.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                excel))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imported").value(1))
                .andExpect(jsonPath("$.data.skipped").value(1));

        mockMvc.perform(get("/api/v1/lots/" + lotId + "/whitelist-vehicles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(get("/api/v1/lots/" + lotId + "/internal-vehicles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void adminCanExportWhitelistVehicles() throws Exception {
        String token = adminToken();
        String lotId = createLot(token);

        mockMvc.perform(post("/api/v1/lots/" + lotId + "/whitelist-vehicles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plateNumber\":\"京A12345\",\"plateColor\":\"BLUE\",\"ownerName\":\"张三\",\"type\":\"TENANT\",\"department\":\"访客\",\"startTime\":\"2026-08-23T00:00:00Z\",\"endTime\":\"2026-12-31T23:59:59Z\"}"))
                .andExpect(status().isOk());

        MvcResult export = mockMvc.perform(get("/api/v1/lots/" + lotId + "/whitelist-vehicles/export")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"whitelist-vehicles.xlsx\""))
                .andReturn();

        byte[] body = export.getResponse().getContentAsByteArray();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(body))) {
            Row row = workbook.getSheetAt(0).getRow(1);
            org.junit.jupiter.api.Assertions.assertNotNull(row);
            org.junit.jupiter.api.Assertions.assertEquals("京A12345", row.getCell(0).getStringCellValue());
            org.junit.jupiter.api.Assertions.assertEquals("BLUE", row.getCell(2).getStringCellValue());
            org.junit.jupiter.api.Assertions.assertFalse(row.getCell(6).getStringCellValue().isEmpty());
            org.junit.jupiter.api.Assertions.assertFalse(row.getCell(7).getStringCellValue().isEmpty());
            org.junit.jupiter.api.Assertions.assertEquals("TENANT", row.getCell(8).getStringCellValue());
        }
    }

    private byte[] buildAccessListExcel() throws Exception {
        String[][] rows = {
            { "车牌号", "车主姓名", "车牌颜色", "电话", "部门", "备注", "开始时间", "结束时间", "类型" },
            { "京A10001", "张三", "BLUE", "13800000000", "访客", "", "2026-01-01 00:00", "2026-12-31 23:59", "租户" },
            { "京A10001", "李四", "BLUE", "", "", "", "2026-01-01 00:00", "2026-12-31 23:59", "" },
        };
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("whitelist");
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
