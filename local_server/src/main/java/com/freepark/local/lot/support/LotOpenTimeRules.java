package com.freepark.local.lot.support;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 车场对外开放时段（{@link com.freepark.local.domain.ParkingLot#getOpenTimeRules()}）的编解码与判定。
 *
 * <p>入库形态为 JSON 数组字符串，元素 {"day":1,"start":"08:00","end":"20:00"}：
 * day 为 ISO 周几（1=周一 … 7=周日），同一天可配置多个时段；start/end 为分钟精度的钟面时间。</p>
 */
public final class LotOpenTimeRules {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private LotOpenTimeRules() {
    }

    /** 一个对外开放时段。 */
    public record Window(int day, LocalTime start, LocalTime end) {
    }

    /** 时段列表 → 入库 JSON；无有效时段返回 null（表示不对外开放）。 */
    public static String serialize(List<Window> windows) {
        if (windows == null || windows.isEmpty()) {
            return null;
        }
        ArrayNode array = JsonNodeFactory.instance.arrayNode();
        for (Window window : windows) {
            ObjectNode node = JsonNodeFactory.instance.objectNode();
            node.put("day", window.day());
            node.put("start", window.start().format(TIME_FORMAT));
            node.put("end", window.end().format(TIME_FORMAT));
            array.add(node);
        }
        return array.toString();
    }

    public static List<Window> parse(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return parse(MAPPER.readTree(json));
        } catch (Exception ex) {
            return List.of();
        }
    }

    /** 同步下发的 JSON 数组节点 → 时段列表；非数组或脏数据返回空列表（视为不对外开放）。 */
    public static List<Window> parse(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<Window> windows = new ArrayList<>(node.size());
        for (JsonNode item : node) {
            int day = item.path("day").asInt(0);
            LocalTime start = parseTime(item.path("start").asText(""));
            LocalTime end = parseTime(item.path("end").asText(""));
            if (day < 1 || day > 7 || start == null || end == null || !start.isBefore(end)) {
                continue;
            }
            windows.add(new Window(day, start, end));
        }
        return windows;
    }

    /** 站点本地时刻是否落在任一时段内。 */
    public static boolean isOpenAt(String json, LocalDateTime siteWall) {
        if (siteWall == null) {
            return false;
        }
        int day = siteWall.getDayOfWeek().getValue();
        LocalTime time = siteWall.toLocalTime();
        for (Window window : parse(json)) {
            if (window.day() == day && !time.isBefore(window.start()) && time.isBefore(window.end())) {
                return true;
            }
        }
        return false;
    }

    public static LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(value.trim(), TIME_FORMAT);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    /** 钟面时间 → HH:mm。 */
    public static String formatTime(LocalTime time) {
        return time.format(TIME_FORMAT);
    }
}
