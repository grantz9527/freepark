package com.freepark.local.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
class InternalVehicleControllerTest {

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
                        .content("{\"name\":\"Internal Lot\",\"code\":\"" + code
                                + "\",\"lotType\":\"INTERNAL\",\"totalSpaces\":50}"))
                .andExpect(status().isOk())
                .andReturn();
        return jsonMapper.readTree(create.getResponse().getContentAsString())
                .get("data").get("id").asString();
    }

    @Test
    void adminCanManageInternalVehicles() throws Exception {
        String token = adminToken();
        String lotId = createLot(token);

        mockMvc.perform(post("/api/v1/lots/" + lotId + "/internal-vehicles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plateNumber\":\"京A12345\",\"plateColor\":\"BLUE\",\"ownerName\":\"张三\",\"phone\":\"13800000000\",\"department\":\"行政部\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plateNumber").value("京A12345"))
                .andExpect(jsonPath("$.data.plateColor").value("BLUE"));

        mockMvc.perform(get("/api/v1/lots/" + lotId + "/internal-vehicles")
                        .header("Authorization", "Bearer " + token)
                        .param("plate", "12345"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        MvcResult create = mockMvc.perform(post("/api/v1/lots/" + lotId + "/internal-vehicles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plateNumber\":\"京B88888\",\"plateColor\":\"GREEN\",\"ownerName\":\"李四\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String vehicleId = jsonMapper.readTree(create.getResponse().getContentAsString())
                .get("data").get("id").asString();

        mockMvc.perform(put("/api/v1/lots/" + lotId + "/internal-vehicles/" + vehicleId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plateNumber\":\"京B99999\",\"plateColor\":\"YELLOW\",\"ownerName\":\"李四\",\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plateNumber").value("京B99999"))
                .andExpect(jsonPath("$.data.plateColor").value("YELLOW"))
                .andExpect(jsonPath("$.data.enabled").value(false));

        mockMvc.perform(delete("/api/v1/lots/" + lotId + "/internal-vehicles/" + vehicleId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanImportAndDeleteVehicleBatch() throws Exception {
        String token = adminToken();
        String lotId = createLot(token);

        mockMvc.perform(post("/api/v1/lots/" + lotId + "/internal-vehicles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plateNumber\":\"京A00001\",\"plateColor\":\"BLUE\",\"ownerName\":\"张三\"}"))
                .andExpect(status().isOk());

        byte[] excel = buildVehicleExcel();
        MvcResult importResult = mockMvc.perform(multipart("/api/v1/lots/" + lotId + "/internal-vehicles/import")
                        .file(new MockMultipartFile(
                                "file",
                                "vehicles.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                excel))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imported").value(2))
                .andExpect(jsonPath("$.data.skipped").value(1))
                .andReturn();
        String batchId = jsonMapper.readTree(importResult.getResponse().getContentAsString())
                .get("data").get("batchId").asString();

        mockMvc.perform(get("/api/v1/lots/" + lotId + "/internal-vehicles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3));

        mockMvc.perform(delete("/api/v1/lots/" + lotId + "/internal-vehicles/batch/" + batchId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(2));

        mockMvc.perform(get("/api/v1/lots/" + lotId + "/internal-vehicles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    private byte[] buildVehicleExcel() throws Exception {
        String[][] rows = {
            { "车牌号", "车主姓名", "车牌颜色", "电话", "部门", "备注" },
            { "京A00001", "张三", "", "", "", "" },
            { "京A00002", "王五", "BLUE", "13800000000", "行政部", "" },
            { "京A00003", "赵六", "绿色", "", "", "" },
        };
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("vehicles");
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
