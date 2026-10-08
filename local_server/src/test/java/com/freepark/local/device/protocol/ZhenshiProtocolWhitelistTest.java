package com.freepark.local.device.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

class ZhenshiProtocolWhitelistTest {

    private final ZhenshiProtocol protocol = new ZhenshiProtocol(null, JsonMapper.builder().build(), null);

    @Test
    void addWhitelistIsCarriedOnPushResponse() {
        JsonNode base = protocol.buildPushResponse(true);
        JsonNode out = protocol.appendWhitelistOperate(base, new ZhenshiWhitelistBatch(
                ZhenshiWhitelistBatch.ADD,
                7,
                null,
                List.of(new ZhenshiWhitelistBatch.Item(
                        "京A12345", "2018-01-01 11:11:11", "2019-01-01 11:11:11"))));

        assertEquals("ok", out.path("Response_AlarmInfoPlate").path("info").asText());
        JsonNode op = out.path("Response_AlarmInfoPlate").path("white_list_operate");
        assertEquals(0, op.path("operate_type").asInt());
        assertEquals(7, op.path("msg_id").asLong());
        assertFalse(op.has("reply_url"));
        JsonNode row = op.path("white_list_data").get(0);
        assertEquals("京A12345", row.path("plate").asText());
        assertEquals(1, row.path("enable").asInt());
        assertEquals(0, row.path("need_alarm").asInt());
        assertEquals("2018-01-01 11:11:11", row.path("enable_time").asText());
        assertEquals("2019-01-01 11:11:11", row.path("overdue_time").asText());
    }

    @Test
    void deleteWhitelistOnHeartbeatBecomesAlarmResponse() {
        ObjectNode heartbeat = JsonNodeFactory.instance.objectNode();
        heartbeat.put("status", "ok");
        JsonNode out = protocol.appendWhitelistOperate(heartbeat, new ZhenshiWhitelistBatch(
                ZhenshiWhitelistBatch.DELETE,
                1,
                null,
                List.of(new ZhenshiWhitelistBatch.Item("川A12345", null, null))));

        assertEquals("no", out.path("Response_AlarmInfoPlate").path("info").asText());
        JsonNode op = out.path("Response_AlarmInfoPlate").path("white_list_operate");
        assertEquals(1, op.path("operate_type").asInt());
        JsonNode row = op.path("white_list_data").get(0);
        assertEquals("川A12345", row.path("plate").asText());
        assertFalse(row.has("enable"));
        assertFalse(row.has("need_alarm"));
    }

    @Test
    void clearAllDeleteUsesEmptyPlate() {
        JsonNode out = protocol.appendWhitelistOperate(protocol.buildPushResponse(false), new ZhenshiWhitelistBatch(
                ZhenshiWhitelistBatch.DELETE,
                9,
                null,
                List.of(new ZhenshiWhitelistBatch.Item("", null, null))));
        JsonNode op = out.path("Response_AlarmInfoPlate").path("white_list_operate");
        assertEquals(1, op.path("operate_type").asInt());
        assertEquals("", op.path("white_list_data").get(0).path("plate").asText());
    }
}
